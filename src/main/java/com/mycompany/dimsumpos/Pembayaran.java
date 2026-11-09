package com.mycompany.dimsumpos;

// Superclass Pembayaran (Inheritance)
public class Pembayaran {
    private int totalBayar;
    private String metode;

    public Pembayaran(int totalBayar, String metode) {
        this.totalBayar = totalBayar;
        this.metode = metode;
    }

    public int getTotalBayar() { return totalBayar; }
    public String getMetode() { return metode; }

    // Method ini akan ditimpa (override) oleh subclass
    public void tampilkanInstruksi() {
        System.out.println("Silakan lakukan pembayaran sebesar Rp" + getTotalBayar());
    }
}
