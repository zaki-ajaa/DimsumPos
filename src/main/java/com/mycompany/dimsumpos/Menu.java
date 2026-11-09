package com.mycompany.dimsumpos;

// Class Menu: menyimpan data produk yang dijual (Encapsulation)
public class Menu {
    private String kode;
    private String nama;
    private int harga;
    private int stok;

    public Menu(String kode, String nama, int harga, int stok) {
        this.kode = kode;
        this.nama = nama;
        this.harga = harga;
        this.stok = stok;
    }

    // Getter
    public String getKode() { return kode; }
    public String getNama() { return nama; }
    public int getHarga() { return harga; }
    public int getStok() { return stok; }

    // Setter (dengan validasi sederhana)
    public void setHarga(int harga) {
        if (harga > 0) {
            this.harga = harga;
        }
    }

    public void setStok(int stok) {
        if (stok >= 0) {
            this.stok = stok;
        }
    }

    public void kurangiStok(int jumlah) {
        if (jumlah > 0 && jumlah <= stok) {
            stok -= jumlah;
        }
    }
}
