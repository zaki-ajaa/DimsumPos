package kasir;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server HTTP lokal untuk antarmuka HTML/CSS/JS.
 * Memakai com.sun.net.httpserver bawaan JDK (tanpa instalasi tambahan).
 *   - File tampilan dilayani dari folder `static/`.
 *   - Semua data & logika tetap diproses Java lewat TokoService (JSON API /api/...).
 * Mencoba port berikutnya bila port sedang dipakai.
 */
public class AplikasiWeb {

    /** Padanan LookupError di Python: rute/data tidak ditemukan (HTTP 404). */
    static class TidakDitemukan extends RuntimeException {
        private static final long serialVersionUID = 1L;

        TidakDitemukan(String pesan) {
            super(pesan);
        }
    }

    private static final List<String> FIELD_PRODUK = List.of("sku", "nama", "kategori", "harga", "hpp", "stok");

    private final TokoService layanan;
    private final Path folderStatic;
    private final HttpServer server;
    public final String url;

    public AplikasiWeb(TokoService service, Path folderStatic, String host, int port) {
        this.layanan = service;
        this.folderStatic = folderStatic.toAbsolutePath().normalize();
        HttpServer dibuat = null;
        for (int p = port; p < port + 10; p++) {
            try {
                dibuat = HttpServer.create(new InetSocketAddress(host, p), 0);
                break;
            } catch (IOException e) { // port sibuk -> coba port lain
                continue;
            }
        }
        if (dibuat == null) {
            throw new IllegalStateException("Port " + port + "-" + (port + 9) + " semuanya sedang dipakai");
        }
        this.server = dibuat;
        this.server.createContext("/", this::tangani);
        this.url = "http://" + host + ":" + server.getAddress().getPort();
    }

    public AplikasiWeb(TokoService service, Path folderStatic) {
        this(service, folderStatic, "127.0.0.1", 8000);
    }

    /** Mulai melayani request (tidak memblokir). */
    public void mulai() {
        server.start();
    }

    public void berhenti() {
        server.stop(0);
    }

    /** Mulai melayani request sampai Ctrl+C. */
    public void jalankan(boolean bukaBrowser) {
        System.out.println("UniCashier Dimsum Mentai berjalan di " + url + "  (Ctrl+C untuk berhenti)");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nServer dihentikan.");
            server.stop(0);
        }));
        mulai();
        if (bukaBrowser) {
            try {
                if (!GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()
                        && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(URI.create(url));
                }
            } catch (Exception e) {
                // gagal membuka browser: abaikan, pengguna bisa membuka alamat secara manual
            }
        }
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------- helper respons ----------
    private void kirim(HttpExchange ex, int status, byte[] isi, String tipe) throws IOException {
        ex.getResponseHeaders().set("Content-Type", tipe);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, isi.length == 0 ? -1 : isi.length);
        if (isi.length > 0) {
            try (OutputStream os = ex.getResponseBody()) {
                os.write(isi);
            }
        } else {
            ex.close();
        }
    }

    private void kirimJson(HttpExchange ex, Object data, int status) throws IOException {
        kirim(ex, status, Json.tulis(data).getBytes(StandardCharsets.UTF_8), "application/json; charset=utf-8");
    }

    private static Map<String, Object> galat(String pesan) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", pesan);
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> bacaJson(HttpExchange ex) throws IOException {
        byte[] mentah;
        try (InputStream in = ex.getRequestBody()) {
            mentah = in.readAllBytes();
        }
        String teks = mentah.length == 0 ? "{}" : new String(mentah, StandardCharsets.UTF_8);
        Object data;
        try {
            data = Json.baca(teks);
        } catch (IllegalArgumentException e) {
            throw new ValidasiError("Data yang dikirim bukan JSON yang valid");
        }
        if (!(data instanceof Map)) {
            throw new ValidasiError("Data harus berupa objek JSON");
        }
        return (Map<String, Object>) data;
    }

    // ---------- util URL ----------
    /** Padanan urllib.parse.unquote: %XX dibuka, tanda '+' dibiarkan. */
    private static String unquote(String s) {
        return URLDecoder.decode(s.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    /** Padanan parse_qs: nilai kosong dibuang, nilai pertama dipakai. */
    private static Map<String, String> parseQuery(String q) {
        Map<String, String> hasil = new LinkedHashMap<>();
        if (q == null || q.isEmpty()) {
            return hasil;
        }
        for (String pasang : q.split("&")) {
            int sama = pasang.indexOf('=');
            if (sama < 0) {
                continue;
            }
            String k = URLDecoder.decode(pasang.substring(0, sama), StandardCharsets.UTF_8);
            String v = URLDecoder.decode(pasang.substring(sama + 1), StandardCharsets.UTF_8);
            if (!v.isEmpty()) {
                hasil.putIfAbsent(k, v);
            }
        }
        return hasil;
    }

    // ---------- routing ----------
    private void tangani(HttpExchange ex) throws IOException {
        try {
            String metode = ex.getRequestMethod();
            String jalur = ex.getRequestURI().getRawPath();
            if (jalur == null) {
                jalur = "/";
            }
            if (jalur.startsWith("/api/")) {
                tanganiApi(ex, metode, jalur);
            } else if (metode.equals("GET")) {
                kirimStatis(ex, jalur);
            } else {
                kirim(ex, 405, "Metode tidak diizinkan".getBytes(StandardCharsets.UTF_8), "text/plain; charset=utf-8");
            }
        } catch (Exception e) { // server tidak boleh mati karena satu request
            try {
                kirimJson(ex, galat("Terjadi kesalahan pada server"), 500);
            } catch (Exception ignore) {
                ex.close();
            }
        }
    }

    /** Jalankan rute API; ubah error menjadi respons JSON yang rapi. */
    private void tanganiApi(HttpExchange ex, String metode, String jalur) throws IOException {
        Map<String, String> query = parseQuery(ex.getRequestURI().getRawQuery());
        List<String> bagian = new ArrayList<>();
        for (String b : jalur.replaceAll("^/+|/+$", "").split("/", -1)) {
            bagian.add(unquote(b));
        }
        try {
            kirimJson(ex, ruteApi(ex, metode, bagian, query), 200);
        } catch (IllegalArgumentException e) { // termasuk ValidasiError
            kirimJson(ex, galat(e.getMessage()), 400);
        } catch (TidakDitemukan e) {
            kirimJson(ex, galat("Rute tidak ditemukan"), 404);
        }
    }

    private static Object ambilOr(Map<String, Object> peta, String kunci, Object bawaan) {
        return peta.containsKey(kunci) ? peta.get(kunci) : bawaan;
    }

    /** Peta rute: (metode, /api/<sumber>[/<id>]) -> pemanggilan service. */
    private Object ruteApi(HttpExchange ex, String metode, List<String> bagian, Map<String, String> q) throws IOException {
        TokoService s = layanan;
        String sumber = bagian.size() > 1 ? bagian.get(1) : "";
        String id = bagian.size() > 2 ? bagian.get(2) : null;
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("ok", true);

        if (sumber.equals("meta") && metode.equals("GET")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("kategori", Validasi.KATEGORI);
            m.put("metode", Validasi.METODE);
            m.put("batas_menipis", Produk.BATAS_STOK_MENIPIS);
            m.put("peringatan", s.peringatan());
            return m;
        }

        if (sumber.equals("produk")) {
            if (metode.equals("GET") && id == null) {
                Map<String, Object> kriteria = new LinkedHashMap<>();
                kriteria.put("kata", q.getOrDefault("kata", ""));
                kriteria.put("kategori", q.get("kategori"));
                kriteria.put("stok_maks", "1".equals(q.get("menipis")) ? Produk.BATAS_STOK_MENIPIS : null);
                List<Produk> hasil = s.daftarProduk(q.get("urut"), "desc".equals(q.get("arah")), kriteria);
                List<Object> baris = new ArrayList<>();
                for (Produk p : hasil) {
                    baris.add(p.keMap());
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("produk", baris);
                m.put("menipis", s.stokMenipis().size());
                m.put("total", s.getProduk().size());
                return m;
            }
            if (metode.equals("POST") && id == null) {
                Map<String, Object> body = bacaJson(ex);
                s.tambahProduk(ambilField(body));
                return ok;
            }
            if (metode.equals("PUT") && id != null && !id.isEmpty()) {
                Map<String, Object> body = bacaJson(ex);
                s.ubahProduk(id, ambilField(body));
                return ok;
            }
            if (metode.equals("DELETE") && id != null && !id.isEmpty()) {
                s.hapusProduk(id);
                return ok;
            }
        }

        if (sumber.equals("checkout") && metode.equals("POST")) {
            Map<String, Object> body = bacaJson(ex);
            Object items = body.get("items");
            if (!(items instanceof List)) {
                throw new ValidasiError("Daftar item tidak valid");
            }
            Keranjang keranjang = new Keranjang();
            for (Object it : (List<?>) items) {
                Produk produk = null;
                if (it instanceof Map) {
                    Map<?, ?> itemMap = (Map<?, ?>) it;
                    Object sku = itemMap.containsKey("sku") ? itemMap.get("sku") : "";
                    produk = s.cariSku(String.valueOf(sku));
                }
                if (produk == null) {
                    throw new ValidasiError("Ada produk yang tidak ditemukan");
                }
                Object qty = ((Map<?, ?>) it).containsKey("qty") ? ((Map<?, ?>) it).get("qty") : 0;
                keranjang.tambah(produk, Validasi.keInt(qty, "Jumlah"));
            }
            int uang = Validasi.keInt(ambilOr(body, "uang", 0), "Uang diterima");
            String metodeBayar = String.valueOf(ambilOr(body, "metode", ""));
            int diskon = Validasi.keInt(ambilOr(body, "diskon", 0), "Diskon");
            TokoService.HasilCheckout hasil = s.checkout(keranjang, metodeBayar, diskon, uang);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("transaksi", hasil.transaksi().keMap());
            m.put("kembalian", hasil.kembalian());
            m.put("struk", TokoService.strukTeks(hasil.transaksi(), uang, hasil.kembalian()));
            return m;
        }

        if (sumber.equals("transaksi") && metode.equals("GET")) {
            String urut = q.get("urut");
            List<Transaksi> hasil = s.daftarTransaksi(q.getOrDefault("kata", ""),
                    urut == null ? "waktu" : urut, q.getOrDefault("arah", "desc").equals("desc"));
            List<Object> baris = new ArrayList<>();
            for (Transaksi t : hasil) {
                baris.add(t.keMap());
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("transaksi", baris);
            return m;
        }

        if (sumber.equals("struk") && metode.equals("GET") && id != null && !id.isEmpty()) {
            Transaksi trx = s.ambilTransaksi(id);
            if (trx == null) {
                throw new TidakDitemukan("Transaksi tidak ada");
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("struk", TokoService.strukTeks(trx));
            return m;
        }

        if (sumber.equals("laporan") && metode.equals("GET")) {
            List<Object> harian = new ArrayList<>();
            for (TokoService.Harian h : s.ringkasanHarian()) {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("tanggal", h.tanggal());
                r.put("jumlah", h.jumlah());
                r.put("pendapatan", h.pendapatan());
                r.put("hpp", h.hpp());
                r.put("laba", h.laba());
                harian.add(r);
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("total", s.ringkasanTotal());
            m.put("harian", harian);
            m.put("menipis", s.stokMenipis().size());
            return m;
        }

        throw new TidakDitemukan("Rute tidak dikenal");
    }

    /** Ambil field produk dari body JSON (field yang hilang diisi ""). */
    private static Map<String, Object> ambilField(Map<String, Object> body) {
        Map<String, Object> hasil = new LinkedHashMap<>();
        for (String k : FIELD_PRODUK) {
            hasil.put(k, ambilOr(body, k, ""));
        }
        return hasil;
    }

    // ---------- berkas statis ----------
    private static String tipeBerkas(String nama) {
        String n = nama.toLowerCase(java.util.Locale.ROOT);
        String tipe;
        if (n.endsWith(".html") || n.endsWith(".htm")) {
            tipe = "text/html";
        } else if (n.endsWith(".css")) {
            tipe = "text/css";
        } else if (n.endsWith(".js")) {
            tipe = "text/javascript";
        } else if (n.endsWith(".json")) {
            tipe = "application/json";
        } else if (n.endsWith(".txt")) {
            tipe = "text/plain";
        } else if (n.endsWith(".svg")) {
            tipe = "image/svg+xml";
        } else if (n.endsWith(".png")) {
            tipe = "image/png";
        } else if (n.endsWith(".jpg") || n.endsWith(".jpeg")) {
            tipe = "image/jpeg";
        } else if (n.endsWith(".gif")) {
            tipe = "image/gif";
        } else if (n.endsWith(".ico")) {
            tipe = "image/x-icon";
        } else if (n.endsWith(".woff2")) {
            tipe = "font/woff2";
        } else if (n.endsWith(".woff")) {
            tipe = "font/woff";
        } else {
            tipe = "application/octet-stream";
        }
        return tipe.startsWith("text/") ? tipe + "; charset=utf-8" : tipe;
    }

    private void kirimStatis(HttpExchange ex, String jalurMentah) throws IOException {
        String jalur = unquote(jalurMentah);
        if (jalur.isEmpty() || jalur.equals("/")) {
            jalur = "/index.html";
        }
        byte[] isi;
        Path lokasi;
        try {
            lokasi = folderStatic.resolve(jalur.replaceFirst("^/+", "")).normalize();
            // cegah path traversal (../) keluar dari folder static
            if (!lokasi.startsWith(folderStatic) || lokasi.equals(folderStatic)) {
                kirim(ex, 404, "Tidak ditemukan".getBytes(StandardCharsets.UTF_8), "text/plain; charset=utf-8");
                return;
            }
            isi = Files.readAllBytes(lokasi);
        } catch (IOException | InvalidPathException e) { // file tidak ada / tidak bisa dibaca
            kirim(ex, 404, "Tidak ditemukan".getBytes(StandardCharsets.UTF_8), "text/plain; charset=utf-8");
            return;
        }
        kirim(ex, 200, isi, tipeBerkas(lokasi.getFileName().toString()));
    }
}
