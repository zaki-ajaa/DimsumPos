package kasir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit test sederhana (tanpa library) untuk logika kasir - 18 test, 3 kelompok.
 * Jalankan dari folder proyek:  java -cp out kasir.TestKasir
 */
public class TestKasir {
    static int lulus = 0;
    static int gagal = 0;

    interface Uji { void jalan() throws Exception; }

    static void test(String nama, Uji uji) {
        try {
            uji.jalan();
            lulus++;
            System.out.println("  ok    " + nama);
        } catch (Throwable e) {
            gagal++;
            System.out.println("  GAGAL " + nama + " -> " + e);
        }
    }

    static void cek(boolean kondisi, String pesan) {
        if (!kondisi) {
            throw new AssertionError(pesan);
        }
    }

    static void sama(Object diharap, Object hasil) {
        if (!java.util.Objects.equals(diharap, hasil)) {
            throw new AssertionError("diharapkan " + diharap + " tetapi hasilnya " + hasil);
        }
    }

    static void harusValidasiError(Uji uji) throws Exception {
        try {
            uji.jalan();
        } catch (ValidasiError e) {
            return;
        }
        throw new AssertionError("seharusnya melempar ValidasiError");
    }

    static Path tmp() throws IOException {
        return Files.createTempDirectory("kasir_test");
    }

    static void hapusFolder(Path p) throws IOException {
        try (var s = Files.walk(p)) {
            s.sorted(Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
        }
    }

    static Map<String, Object> peta(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("== TestAlgoritma ==");
        test("merge_sort naik dan turun", () -> {
            sama(List.of(1, 2, 5, 9), Algoritma.mergeSort(List.of(5, 2, 9, 1)));
            sama(List.of(9, 5, 2, 1), Algoritma.mergeSort(List.of(5, 2, 9, 1), x -> x, true));
        });
        test("merge_sort stabil dan key", () -> {
            List<String[]> data = List.of(new String[] {"b", "1"}, new String[] {"a", "1"}, new String[] {"c", "0"});
            List<String[]> h = Algoritma.mergeSort(data, x -> x[1], false);
            sama("c", h.get(0)[0]);
            sama("b", h.get(1)[0]);
            sama("a", h.get(2)[0]);
        });
        test("binary_search", () -> {
            List<Integer> data = List.of(1, 3, 5, 7, 9);
            sama(3, Algoritma.binarySearch(data, 7));
            sama(-1, Algoritma.binarySearch(data, 4));
        });
        test("cari_linear tidak peka huruf", () -> {
            List<String> data = List.of("Mentai Keju", "Es Teh", "mentai pedas");
            sama(List.of("Mentai Keju", "mentai pedas"), Algoritma.cariLinear(data, "MENTAI", x -> x));
        });

        System.out.println("== TestLayanan ==");
        test("validasi harga negatif -> error", () ->
                harusValidasiError(() -> Validasi.validasiProduk("X1", "Tes", "Dimsum", -1000, 0, 5)));
        test("validasi field wajib", () ->
                harusValidasiError(() -> Validasi.validasiProduk("", "Tes", "Dimsum", 1000, 500, 5)));

        Path folder = tmp();
        try {
            TokoService service = new TokoService(new Storage(folder));
            test("filter dengan kriteria (kwargs)", () -> {
                List<Produk> hasil = service.filterProduk(peta("kata", "mentai", "kategori", "Dimsum", "harga_maks", 17000));
                cek(!hasil.isEmpty(), "hasil kosong");
                cek(hasil.stream().allMatch(p -> p.getKategori().equals("Dimsum") && p.getHarga() <= 17000), "filter salah");
                harusValidasiError(() -> service.filterProduk(peta("warna", "merah")));
            });
            test("hitung_total varargs", () -> {
                sama(4000L, TokoService.hitungTotal(1000, 2500, 500));
                sama(0L, TokoService.hitungTotal());
            });
            test("cari_sku binary search", () -> {
                sama("Dimsum Mentai Original", service.cariSku("dm001").getNama());
                cek(service.cariSku("TIDAKADA") == null, "seharusnya null");
            });
            test("keranjang melebihi stok", () -> {
                Keranjang k = new Keranjang();
                Produk p = service.cariSku("DM005"); // stok 8
                harusValidasiError(() -> k.tambah(p, p.getStok() + 1));
            });
            test("checkout kurangi stok dan kembalian", () -> {
                Keranjang k = new Keranjang();
                Produk p = service.cariSku("DM001");
                int stokAwal = p.getStok();
                k.tambah(p, 2); // 2 x 15.000 = 30.000
                TokoService.HasilCheckout h = service.checkout(k, "Tunai", 10, 50000);
                sama(27000, h.transaksi().getTotal());
                sama(23000, h.kembalian());
                sama(stokAwal - 2, p.getStok());
                sama(27000L, service.ringkasanTotal().get("pendapatan"));
            });
            test("checkout uang kurang -> error", () -> {
                Keranjang k = new Keranjang();
                k.tambah(service.cariSku("DM001"), 1);
                harusValidasiError(() -> service.checkout(k, "Tunai", 0, 1000));
            });
            test("data tersimpan permanen", () -> {
                Path f = tmp();
                try {
                    TokoService s1 = new TokoService(new Storage(f));
                    Keranjang k = new Keranjang();
                    k.tambah(s1.cariSku("MN001"), 1);
                    s1.checkout(k, "QRIS");
                    TokoService s2 = new TokoService(new Storage(f)); // simulasi buka aplikasi lagi
                    sama(1, s2.getTransaksi().size());
                    sama(49, s2.cariSku("MN001").getStok());
                } finally {
                    hapusFolder(f);
                }
            });
            test("daftar produk terurut harga", () -> {
                List<Integer> naik = new ArrayList<>();
                service.daftarProduk("harga", false).forEach(p -> naik.add(p.getHarga()));
                List<Integer> turun = new ArrayList<>();
                service.daftarProduk("harga", true).forEach(p -> turun.add(p.getHarga()));
                List<Integer> urutNaik = new ArrayList<>(naik);
                java.util.Collections.sort(urutNaik);
                List<Integer> urutTurun = new ArrayList<>(urutNaik);
                java.util.Collections.reverse(urutTurun);
                sama(urutNaik, naik);
                sama(urutTurun, turun);
                harusValidasiError(() -> service.daftarProduk("password", false));
            });
            test("file rusak tidak crash", () -> {
                Path f = tmp();
                try {
                    Files.writeString(f.resolve("produk.csv"), "sku,nama,kategori,harga,hpp,stok\nX1,Tes,Dimsum,abc,1,2\n");
                    TokoService s = new TokoService(new Storage(f));
                    cek(!s.peringatan().isEmpty(), "harus ada peringatan");
                    cek(!s.getProduk().isEmpty(), "produk harus jatuh ke data awal");
                } finally {
                    hapusFolder(f);
                }
            });
        } finally {
            hapusFolder(folder);
        }

        System.out.println("== TestApiWeb ==");
        Path folderApi = tmp();
        AplikasiWeb app = new AplikasiWeb(new TokoService(new Storage(folderApi)),
                Path.of("static"), "127.0.0.1", 0);
        app.mulai();
        HttpClient klien = HttpClient.newHttpClient();
        try {
            test("halaman utama dan path traversal", () -> {
                HttpResponse<String> r = panggil(klien, app.url + "/", "GET", null);
                sama(200, r.statusCode());
                cek(r.body().contains("UniCashier"), "isi halaman tidak sesuai");
                sama(404, panggil(klien, app.url + "/..%2Fmain.py", "GET", null).statusCode());
            });
            test("produk urut dan validasi", () -> {
                HttpResponse<String> r = panggil(klien, app.url + "/api/produk?urut=harga&arah=desc", "GET", null);
                sama(200, r.statusCode());
                @SuppressWarnings("unchecked")
                List<Object> produk = (List<Object>) ((Map<String, Object>) Json.baca(r.body())).get("produk");
                List<Long> harga = new ArrayList<>();
                for (Object o : produk) {
                    harga.add((Long) ((Map<?, ?>) o).get("harga"));
                }
                List<Long> urut = new ArrayList<>(harga);
                urut.sort(Comparator.reverseOrder());
                sama(urut, harga);
                HttpResponse<String> r2 = panggil(klien, app.url + "/api/produk", "POST",
                        "{\"sku\":\"X9\",\"nama\":\"Tes\",\"kategori\":\"Dimsum\",\"harga\":\"-5\",\"hpp\":\"1\",\"stok\":\"1\"}");
                sama(400, r2.statusCode());
                cek(r2.body().contains("negatif"), "pesan error tidak sesuai: " + r2.body());
            });
            test("checkout lewat API", () -> {
                HttpResponse<String> r = panggil(klien, app.url + "/api/checkout", "POST",
                        "{\"items\":[{\"sku\":\"MN002\",\"qty\":2}],\"metode\":\"Tunai\",\"diskon\":0,\"uang\":20000}");
                sama(200, r.statusCode());
                @SuppressWarnings("unchecked")
                Map<String, Object> hasil = (Map<String, Object>) Json.baca(r.body());
                sama(18000L, ((Map<?, ?>) hasil.get("transaksi")).get("total"));
                sama(2000L, hasil.get("kembalian"));
                sama(400, panggil(klien, app.url + "/api/checkout", "POST",
                        "{\"items\":[],\"metode\":\"Tunai\"}").statusCode());
            });
        } finally {
            app.berhenti();
            hapusFolder(folderApi);
        }

        System.out.println("\nHasil: " + lulus + " lulus, " + gagal + " gagal");
        if (gagal > 0) {
            System.exit(1);
        }
    }

    static HttpResponse<String> panggil(HttpClient klien, String url, String metode, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json");
        b.method(metode, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return klien.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
}
