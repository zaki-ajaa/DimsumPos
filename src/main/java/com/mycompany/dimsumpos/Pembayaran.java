package com.mycompany.dimsumpos;

public class Pembayaran {
    protected int totalBayar;
    protected String metode;

    public Pembayaran(int totalBayar, String metode) {
        this.totalBayar = totalBayar;
        this.metode = metode;
    }

    public int getTotalBayar() { return totalBayar; }
    public String getMetode() { return metode; }


    // Teks instruksi pembayaran (dipakai oleh tampilan GUI)
    public String getInstruksi() {
        return "Silakan lakukan pembayaran sebesar Rp" + totalBayar;
    }

    // Versi terminal, ditimpa (override) oleh subclass

    public void tampilkanInstruksi() {
        System.out.println(getInstruksi());
    }
}
//p
