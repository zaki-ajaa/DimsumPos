package kasir;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Penyimpanan data permanen ke file CSV.
 * Mode file yang dipakai (padanan open() di Python):
 *   'r' = membaca produk/transaksi,   'w' = menulis ulang produk (TRUNCATE_EXISTING),
 *   'a' = menambah transaksi baru (APPEND),  'x' = membuat file baru (CREATE_NEW, gagal bila sudah ada).
 */
public class Storage {
    static final List<String> KOLOM_PRODUK = List.of("sku", "nama", "kategori", "harga", "hpp", "stok");
    static final List<String> KOLOM_TRX = List.of("id", "waktu", "items", "subtotal", "diskon", "total", "hpp", "metode");

    private final Path fileProduk;
    private final Path fileTrx;
    /** Pesan masalah file (hilang/rusak) yang bisa ditampilkan UI sebagai peringatan. */
    public final List<String> peringatan = new ArrayList<>();
    /** Catatan biasa (bukan masalah), mis. file dibuat otomatis saat pertama dijalankan. */
    public final List<String> info = new ArrayList<>();

    /** Data awal (dipakai saat produk.csv belum ada atau rusak total). */
    static List<Produk> produkAwal() {
        List<Produk> p = new ArrayList<>();
        p.add(new Produk("DM001", "Dimsum Mentai Original", "Dimsum", 15000, 8000, 40));
        p.add(new Produk("DM002", "Dimsum Mentai Keju", "Dimsum", 18000, 10000, 35));
        p.add(new Produk("DM003", "Dimsum Mentai Pedas", "Dimsum", 17000, 9000, 30));
        p.add(new Produk("DM004", "Dimsum Mentai Ayam Udang", "Dimsum", 20000, 11500, 25));
        p.add(new Produk("DM005", "Dimsum Kukus Polos", "Dimsum", 12000, 6000, 8));
        p.add(new Produk("TP001", "Extra Saus Mentai", "Topping", 4000, 1800, 60));
        p.add(new Produk("TP002", "Extra Keju Leleh", "Topping", 5000, 2500, 45));
        p.add(new Produk("TP003", "Sambal Bawang", "Topping", 3000, 1000, 50));
        p.add(new Produk("MN001", "Es Teh Manis", "Minuman", 6000, 1800, 50));
        p.add(new Produk("MN002", "Es Lemon Tea", "Minuman", 9000, 3000, 40));
        p.add(new Produk("MN003", "Air Mineral", "Minuman", 5000, 2000, 9));
        p.add(new Produk("PK001", "Paket Hemat (3 Mentai + Es Teh)", "Paket", 48000, 27000, 20));
        return p;
    }

    public Storage(Path folder) {
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        this.fileProduk = folder.resolve("produk.csv");
        this.fileTrx = folder.resolve("transaksi.csv");
    }

    // ---------------- CSV sederhana (tanpa library) ----------------
    private static String escape(String s) {
        boolean perluKutip = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        return perluKutip ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    private static void tulisBaris(BufferedWriter w, List<String> kolom) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < kolom.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(kolom.get(i)));
        }
        w.write(sb.append("\r\n").toString());
    }

    /** Pecah teks CSV menjadi baris-baris (mendukung tanda kutip, koma, dan baris baru dalam kutipan). */
    static List<List<String>> parseCsv(String teks) {
        List<List<String>> baris = new ArrayList<>();
        List<String> kini = new ArrayList<>();
        StringBuilder sel = new StringBuilder();
        boolean dalamKutip = false;
        boolean adaIsi = false; // baris ini sudah punya karakter/kolom
        for (int i = 0; i < teks.length(); i++) {
            char c = teks.charAt(i);
            if (dalamKutip) {
                if (c == '"') {
                    if (i + 1 < teks.length() && teks.charAt(i + 1) == '"') {
                        sel.append('"');
                        i++;
                    } else {
                        dalamKutip = false;
                    }
                } else {
                    sel.append(c);
                }
            } else if (c == '"') {
                dalamKutip = true;
                adaIsi = true;
            } else if (c == ',') {
                kini.add(sel.toString());
                sel.setLength(0);
                adaIsi = true;
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < teks.length() && teks.charAt(i + 1) == '\n') {
                    i++;
                }
                if (adaIsi || sel.length() > 0) {
                    kini.add(sel.toString());
                    baris.add(kini);
                }
                kini = new ArrayList<>();
                sel.setLength(0);
                adaIsi = false;
            } else {
                sel.append(c);
                adaIsi = true;
            }
        }
        if (adaIsi || sel.length() > 0) {
            kini.add(sel.toString());
            baris.add(kini);
        }
        return baris;
    }

    /** Ambil nilai kolom `nama` dari satu baris; null bila kolom tidak ada. */
    private static String ambil(List<String> header, List<String> baris, String nama) {
        int idx = header.indexOf(nama);
        return (idx < 0 || idx >= baris.size()) ? null : baris.get(idx);
    }

    private static int keInt(String s) {
        if (s == null) {
            throw new NumberFormatException("kosong");
        }
        return Integer.parseInt(s.strip());
    }

    // ---------------- produk ----------------
    /** Tulis ulang seluruh produk ke produk.csv (mode 'w'). */
    public void simpanProduk(List<Produk> daftar) {
        try (BufferedWriter w = Files.newBufferedWriter(fileProduk, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            tulisBaris(w, KOLOM_PRODUK);
            for (Produk p : daftar) {
                tulisBaris(w, List.of(p.getSku(), p.getNama(), p.getKategori(),
                        String.valueOf(p.getHarga()), String.valueOf(p.getHpp()), String.valueOf(p.getStok())));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Baca produk.csv (mode 'r') dan kembalikan list Produk.
     * - File hilang       -> dibuat dari data awal (NoSuchFileException ditangani).
     * - Baris rusak       -> dilewati dan dicatat di `peringatan`.
     * - File rusak total  -> memakai data awal agar aplikasi tidak crash.
     */
    public List<Produk> muatProduk() {
        List<Produk> hasil = new ArrayList<>();
        int rusak = 0;
        try {
            String isi = Files.readString(fileProduk, StandardCharsets.UTF_8);
            List<List<String>> semua = parseCsv(isi);
            if (!semua.isEmpty()) {
                List<String> header = semua.get(0);
                for (int no = 2; no - 2 < semua.size() - 1; no++) {
                    List<String> b = semua.get(no - 1);
                    try {
                        String sku = ambil(header, b, "sku");
                        String nama = ambil(header, b, "nama");
                        String kategori = ambil(header, b, "kategori");
                        if (sku == null || nama == null || kategori == null) {
                            throw new NumberFormatException("kolom hilang");
                        }
                        hasil.add(new Produk(sku, nama, kategori, keInt(ambil(header, b, "harga")),
                                keInt(ambil(header, b, "hpp")), keInt(ambil(header, b, "stok"))));
                    } catch (NumberFormatException e) {
                        rusak++;
                        peringatan.add("produk.csv baris " + no + " rusak, dilewati");
                    }
                }
            }
        } catch (NoSuchFileException e) {
            info.add("produk.csv tidak ditemukan, dibuat dari data awal");
            List<Produk> awal = produkAwal();
            simpanProduk(awal);
            return awal;
        } catch (CharacterCodingException e) { // isi file bukan UTF-8 yang valid
            peringatan.add("produk.csv tidak bisa dibaca, memakai data awal");
            return produkAwal();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (hasil.isEmpty() && rusak > 0) { // semua baris rusak -> jangan biarkan toko kosong
            peringatan.add("Tidak ada produk valid, memakai data awal");
            hasil = produkAwal();
        }
        return hasil;
    }

    // ---------------- transaksi ----------------
    /** Buat transaksi.csv + header bila belum ada (mode 'x'). */
    private void pastikanFileTrx() {
        try (BufferedWriter w = Files.newBufferedWriter(fileTrx, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            tulisBaris(w, KOLOM_TRX);
        } catch (FileAlreadyExistsException e) {
            // sudah ada: tidak perlu dibuat lagi
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Tambahkan satu transaksi di akhir file (mode 'a'). */
    public void simpanTransaksi(Transaksi t) {
        pastikanFileTrx();
        try (BufferedWriter w = Files.newBufferedWriter(fileTrx, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            tulisBaris(w, List.of(t.getId(), t.getWaktu(), t.getItems(), String.valueOf(t.getSubtotal()),
                    String.valueOf(t.getDiskon()), String.valueOf(t.getTotal()),
                    String.valueOf(t.getHpp()), t.getMetode()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Baca transaksi.csv (mode 'r'); baris rusak dilewati. */
    public List<Transaksi> muatTransaksi() {
        List<Transaksi> hasil = new ArrayList<>();
        try {
            String isi = Files.readString(fileTrx, StandardCharsets.UTF_8);
            List<List<String>> semua = parseCsv(isi);
            if (!semua.isEmpty()) {
                List<String> header = semua.get(0);
                for (int no = 2; no - 2 < semua.size() - 1; no++) {
                    List<String> b = semua.get(no - 1);
                    try {
                        String id = ambil(header, b, "id");
                        String waktu = ambil(header, b, "waktu");
                        String items = ambil(header, b, "items");
                        String metode = ambil(header, b, "metode");
                        if (id == null || waktu == null || items == null || metode == null) {
                            throw new NumberFormatException("kolom hilang");
                        }
                        hasil.add(new Transaksi(id, waktu, items, keInt(ambil(header, b, "subtotal")),
                                keInt(ambil(header, b, "diskon")), keInt(ambil(header, b, "total")),
                                keInt(ambil(header, b, "hpp")), metode));
                    } catch (NumberFormatException e) {
                        peringatan.add("transaksi.csv baris " + no + " rusak, dilewati");
                    }
                }
            }
        } catch (NoSuchFileException e) {
            pastikanFileTrx();
        } catch (CharacterCodingException e) {
            peringatan.add("transaksi.csv tidak bisa dibaca, riwayat dikosongkan");
            return new ArrayList<>();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return hasil;
    }
}
