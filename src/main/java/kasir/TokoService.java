package kasir;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

/** Logika bisnis kasir (tanpa UI): kelola produk, checkout, laporan. */
public class TokoService {

    /** Hasil checkout: transaksi yang tersimpan dan uang kembalian. */
    public record HasilCheckout(Transaksi transaksi, int kembalian) { }

    /** Satu baris rekap harian. */
    public record Harian(String tanggal, int jumlah, long pendapatan, long hpp, long laba) { }

    /** Format angka menjadi 'Rp 15.000'. */
    public static String rupiah(long angka) {
        return "Rp " + String.format(Locale.US, "%,d", angka).replace(",", ".");
    }

    /** Jumlahkan sejumlah angka (varargs, padanan *args), misal total pendapatan. */
    public static long hitungTotal(long... angka) {
        long total = 0;
        for (long a : angka) {
            total += a;
        }
        return total;
    }

    // Aturan filter produk: nama kriteria -> fungsi lambda (produk, nilai) -> boolean
    private static final Map<String, BiPredicate<Produk, Object>> ATURAN_FILTER = new HashMap<>();
    static {
        ATURAN_FILTER.put("kategori", (p, v) -> p.getKategori().equals(v));
        ATURAN_FILTER.put("harga_min", (p, v) -> p.getHarga() >= ((Number) v).intValue());
        ATURAN_FILTER.put("harga_maks", (p, v) -> p.getHarga() <= ((Number) v).intValue());
        ATURAN_FILTER.put("stok_maks", (p, v) -> p.getStok() <= ((Number) v).intValue());
    }

    // Kolom yang boleh dipakai untuk pengurutan (mencegah akses sembarangan)
    static final List<String> KOLOM_URUT_PRODUK = List.of("sku", "nama", "kategori", "harga", "hpp", "stok");
    static final List<String> KOLOM_URUT_TRX = List.of("id", "waktu", "items", "total", "metode");

    private static Object kolomProduk(Produk p, String kolom) {
        switch (kolom) {
            case "sku": return p.getSku();
            case "nama": return p.getNama();
            case "kategori": return p.getKategori();
            case "harga": return p.getHarga();
            case "hpp": return p.getHpp();
            default: return p.getStok();
        }
    }

    private static Object kolomTransaksi(Transaksi t, String kolom) {
        switch (kolom) {
            case "id": return t.getId();
            case "waktu": return t.getWaktu();
            case "items": return t.getItems();
            case "total": return t.getTotal();
            default: return t.getMetode();
        }
    }

    /** Urutkan list objek menurut `kolom` memakai merge sort buatan sendiri. */
    static <T> List<T> urutkanObjek(List<T> data, String kolom, boolean terbalik,
                                    List<String> kolomBoleh, BiFunction<T, String, Object> ambil) {
        if (!kolomBoleh.contains(kolom)) {
            throw new ValidasiError("Kolom urut tidak dikenal: " + kolom);
        }
        return Algoritma.mergeSort(data, o -> Algoritma.kunciUrut(ambil.apply(o, kolom)), terbalik);
    }

    private final Storage storage;
    private final List<Produk> produk;
    private final List<Transaksi> transaksi;
    private List<Produk> indeksSku = null; // cache list produk terurut SKU untuk binary search

    public TokoService(Storage storage) {
        this.storage = storage;
        this.produk = storage.muatProduk();
        this.transaksi = storage.muatTransaksi();
    }

    public List<Produk> getProduk() { return produk; }
    public List<Transaksi> getTransaksi() { return transaksi; }

    /** Pesan masalah file yang terjadi saat data dimuat. */
    public List<String> peringatan() {
        return storage.peringatan;
    }

    // ---------------- produk ----------------
    /** Simpan produk ke file dan buang cache indeks SKU. */
    private void simpanProduk() {
        indeksSku = null;
        storage.simpanProduk(produk);
    }

    /** Cari satu produk berdasarkan SKU persis (binary search). null bila tidak ada. */
    public Produk cariSku(String sku) {
        if (indeksSku == null) {
            indeksSku = Algoritma.mergeSort(produk, Produk::getSku, false);
        }
        String cari = String.valueOf(sku).strip().toUpperCase(Locale.ROOT);
        int pos = Algoritma.binarySearch(indeksSku, cari, Produk::getSku);
        return pos >= 0 ? indeksSku.get(pos) : null;
    }

    private static Produk validasiDari(Map<String, Object> d) {
        return Validasi.validasiProduk(d.get("sku"), d.get("nama"), d.get("kategori"),
                d.get("harga"), d.get("hpp"), d.get("stok"));
    }

    /** Tambah produk baru. `data` = sku, nama, kategori, harga, hpp, stok. */
    public void tambahProduk(Map<String, Object> data) {
        Produk bersih = validasiDari(data);
        if (cariSku(bersih.getSku()) != null) {
            throw new ValidasiError("SKU " + bersih.getSku() + " sudah dipakai");
        }
        produk.add(bersih);
        simpanProduk();
    }

    /** Ubah produk dengan SKU `skuLama`. */
    public void ubahProduk(String skuLama, Map<String, Object> data) {
        Produk lama = cariSku(skuLama);
        if (lama == null) {
            throw new ValidasiError("Produk tidak ditemukan");
        }
        Produk bersih = validasiDari(data);
        if (!bersih.getSku().equals(lama.getSku()) && cariSku(bersih.getSku()) != null) {
            throw new ValidasiError("SKU " + bersih.getSku() + " sudah dipakai");
        }
        lama.setSku(bersih.getSku());
        lama.setNama(bersih.getNama());
        lama.setKategori(bersih.getKategori());
        lama.setHarga(bersih.getHarga());
        lama.setHpp(bersih.getHpp());
        lama.setStok(bersih.getStok());
        simpanProduk();
    }

    /** Hapus produk berdasarkan SKU. */
    public void hapusProduk(String sku) {
        Produk p = cariSku(sku);
        if (p == null) {
            throw new ValidasiError("Produk tidak ditemukan");
        }
        produk.remove(p);
        simpanProduk();
    }

    /**
     * Cari & saring produk memakai map kriteria (padanan **kwargs).
     * Kriteria: kata (pencarian kata kunci), kategori, harga_min, harga_maks, stok_maks.
     * Nilai null / "" diabaikan.
     */
    public List<Produk> filterProduk(Map<String, Object> kriteria) {
        Map<String, Object> sisa = new LinkedHashMap<>(kriteria);
        Object kata = sisa.remove("kata");
        List<Produk> hasil = Algoritma.cariLinear(new ArrayList<>(produk),
                kata == null ? "" : String.valueOf(kata), Produk::teksCari);
        for (Map.Entry<String, Object> e : sisa.entrySet()) {
            Object nilai = e.getValue();
            if (nilai == null || "".equals(nilai)) {
                continue;
            }
            BiPredicate<Produk, Object> aturan = ATURAN_FILTER.get(e.getKey());
            if (aturan == null) {
                throw new ValidasiError("Filter tidak dikenal: " + e.getKey());
            }
            List<Produk> lolos = new ArrayList<>();
            for (Produk p : hasil) {
                if (aturan.test(p, nilai)) {
                    lolos.add(p);
                }
            }
            hasil = lolos;
        }
        return hasil;
    }

    /** Produk hasil filter, lalu diurutkan menurut kolom `urut` (null = tanpa urut). */
    public List<Produk> daftarProduk(String urut, boolean terbalik, Map<String, Object> kriteria) {
        List<Produk> hasil = filterProduk(kriteria);
        if (urut != null && !urut.isEmpty()) {
            hasil = urutkanObjek(hasil, urut, terbalik, KOLOM_URUT_PRODUK, TokoService::kolomProduk);
        }
        return hasil;
    }

    public List<Produk> daftarProduk(String urut, boolean terbalik) {
        return daftarProduk(urut, terbalik, new LinkedHashMap<>());
    }

    /** Daftar produk yang stoknya menipis. */
    public List<Produk> stokMenipis(int batas) {
        Map<String, Object> k = new LinkedHashMap<>();
        k.put("stok_maks", batas);
        return filterProduk(k);
    }

    public List<Produk> stokMenipis() {
        return stokMenipis(Produk.BATAS_STOK_MENIPIS);
    }

    // ---------------- transaksi ----------------
    /** Buat ID transaksi berikutnya, misal TRX-0007. */
    private String idBaru() {
        int nomor = 0;
        for (Transaksi t : transaksi) {
            try {
                nomor = Math.max(nomor, Integer.parseInt(t.getId().split("-")[1].strip()));
            } catch (ArrayIndexOutOfBoundsException | NumberFormatException e) {
                // ID tidak sesuai format: abaikan
            }
        }
        return String.format("TRX-%04d", nomor + 1);
    }

    /**
     * Proses pembayaran: validasi, kurangi stok, simpan transaksi.
     * Lempar ValidasiError bila keranjang kosong, metode salah, stok kurang,
     * atau uang tunai kurang dari total.
     */
    public HasilCheckout checkout(Keranjang keranjang, String metode, int diskonPersen, int uang) {
        if (keranjang.kosong()) {
            throw new ValidasiError("Keranjang masih kosong");
        }
        if (!Validasi.METODE.contains(metode)) {
            throw new ValidasiError("Metode pembayaran tidak valid");
        }
        int[] hitung = keranjang.hitung(diskonPersen);
        int sub = hitung[0];
        int diskon = hitung[1];
        int total = hitung[2];
        if (metode.equals("Tunai") && uang < total) {
            throw new ValidasiError("Uang diterima kurang dari total");
        }
        for (Keranjang.Item item : keranjang.daftar()) {
            if (cariSku(item.produk.getSku()) != item.produk) {
                throw new ValidasiError("Produk " + item.produk.getNama() + " sudah tidak tersedia");
            }
            if (item.qty > item.produk.getStok()) {
                throw new ValidasiError("Stok " + item.produk.getNama() + " tidak cukup");
            }
        }

        for (Keranjang.Item item : keranjang.daftar()) {
            item.produk.setStok(item.produk.getStok() - item.qty);
        }
        Transaksi trx = new Transaksi(idBaru(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                keranjang.ringkasItem(), sub, diskon, total, keranjang.hppTotal(), metode);
        transaksi.add(trx);
        simpanProduk();
        storage.simpanTransaksi(trx);
        return new HasilCheckout(trx, metode.equals("Tunai") ? uang - total : 0);
    }

    public HasilCheckout checkout(Keranjang keranjang, String metode) {
        return checkout(keranjang, metode, 0, 0);
    }

    /** Cari transaksi berdasarkan kata kunci (ID, waktu, item, metode). */
    public List<Transaksi> cariTransaksi(String kata) {
        return Algoritma.cariLinear(transaksi, kata,
                t -> t.getId() + " " + t.getWaktu() + " " + t.getItems() + " " + t.getMetode());
    }

    /** Transaksi hasil pencarian kata kunci, diurutkan (default: terbaru dulu). */
    public List<Transaksi> daftarTransaksi(String kata, String urut, boolean terbalik) {
        return urutkanObjek(cariTransaksi(kata), urut, terbalik, KOLOM_URUT_TRX, TokoService::kolomTransaksi);
    }

    /** Satu transaksi berdasarkan ID, atau null. */
    public Transaksi ambilTransaksi(String idTrx) {
        for (Transaksi t : transaksi) {
            if (t.getId().equals(idTrx)) {
                return t;
            }
        }
        return null;
    }

    // ---------------- laporan ----------------
    /** Rekap per tanggal, terbaru dulu. */
    public List<Harian> ringkasanHarian() {
        Map<String, long[]> perHari = new LinkedHashMap<>();
        for (Transaksi t : transaksi) {
            long[] r = perHari.computeIfAbsent(t.getWaktu().substring(0, 10), k -> new long[3]);
            r[0] += 1;
            r[1] += t.getTotal();
            r[2] += t.getHpp();
        }
        List<Harian> baris = new ArrayList<>();
        for (Map.Entry<String, long[]> e : perHari.entrySet()) {
            long[] r = e.getValue();
            baris.add(new Harian(e.getKey(), (int) r[0], r[1], r[2], r[1] - r[2]));
        }
        return Algoritma.mergeSort(baris, Harian::tanggal, true);
    }

    /** Total keseluruhan: jumlah, pendapatan, hpp, laba. */
    public Map<String, Object> ringkasanTotal() {
        long pendapatan = 0;
        long hpp = 0;
        for (Transaksi t : transaksi) {
            pendapatan = hitungTotal(pendapatan, t.getTotal());
            hpp = hitungTotal(hpp, t.getHpp());
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("jumlah", transaksi.size());
        m.put("pendapatan", pendapatan);
        m.put("hpp", hpp);
        m.put("laba", pendapatan - hpp);
        return m;
    }

    /** Susun teks struk untuk ditampilkan/dicetak. */
    public static String strukTeks(Transaksi trx, int uang, int kembalian) {
        String garis = "-".repeat(34);
        List<String> baris = new ArrayList<>();
        baris.add("   UNICASHIER DIMSUM MENTAI");
        baris.add(garis);
        baris.add(trx.getId() + "  " + trx.getWaktu());
        baris.add(garis);
        for (String item : trx.getItems().split("; ")) {
            baris.add("- " + item);
        }
        baris.add(garis);
        baris.add("Subtotal : " + rupiah(trx.getSubtotal()));
        if (trx.getDiskon() != 0) {
            baris.add("Diskon   : -" + rupiah(trx.getDiskon()));
        }
        baris.add("TOTAL    : " + rupiah(trx.getTotal()));
        baris.add("Metode   : " + trx.getMetode());
        if (trx.getMetode().equals("Tunai") && uang != 0) {
            baris.add("Tunai    : " + rupiah(uang));
            baris.add("Kembali  : " + rupiah(kembalian));
        }
        baris.add(garis);
        baris.add("   Terima kasih! Selamat menikmati");
        return String.join("\n", baris);
    }

    public static String strukTeks(Transaksi trx) {
        return strukTeks(trx, 0, 0);
    }
}
