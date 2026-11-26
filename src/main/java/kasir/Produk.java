package kasir;

import java.util.LinkedHashMap;
import java.util.Map;

/** Satu baris data produk (satu baris tabel). */
public class Produk {
    public static final int BATAS_STOK_MENIPIS = 10; // stok <= angka ini dianggap menipis

    private String sku;
    private String nama;
    private String kategori;
    private int harga; // harga jual (Rp)
    private int hpp;   // harga pokok penjualan / modal per unit (Rp)
    private int stok;

    public Produk(String sku, String nama, String kategori, int harga, int hpp, int stok) {
        this.sku = sku;
        this.nama = nama;
        this.kategori = kategori;
        this.harga = harga;
        this.hpp = hpp;
        this.stok = stok;
    }

    public String getSku() { return sku; }
    public String getNama() { return nama; }
    public String getKategori() { return kategori; }
    public int getHarga() { return harga; }
    public int getHpp() { return hpp; }
    public int getStok() { return stok; }

    public void setSku(String sku) { this.sku = sku; }
    public void setNama(String nama) { this.nama = nama; }
    public void setKategori(String kategori) { this.kategori = kategori; }
    public void setHarga(int harga) { this.harga = harga; }
    public void setHpp(int hpp) { this.hpp = hpp; }
    public void setStok(int stok) { this.stok = stok; }

    /** True jika stok sudah di bawah/sama dengan batas. */
    public boolean stokMenipis(int batas) {
        return stok <= batas;
    }

    public boolean stokMenipis() {
        return stokMenipis(BATAS_STOK_MENIPIS);
    }

    /** Gabungan SKU, nama, kategori - dipakai fitur pencarian kata kunci. */
    public String teksCari() {
        return sku + " " + nama + " " + kategori;
    }

    /** Bentuk Map (urutan field tetap) untuk dijadikan JSON. */
    public Map<String, Object> keMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sku", sku);
        m.put("nama", nama);
        m.put("kategori", kategori);
        m.put("harga", harga);
        m.put("hpp", hpp);
        m.put("stok", stok);
        return m;
    }
}
