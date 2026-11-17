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
<<<<<<< HEAD

    // Teks instruksi pembayaran (dipakai oleh tampilan GUI)
    public String getInstruksi() {
        return "Silakan lakukan pembayaran sebesar Rp" + totalBayar;
    }

    // Versi terminal, ditimpa (override) oleh subclass
=======
    
>>>>>>> ecb2791f07d28f9331024f6678f022f32c3a9bac
    public void tampilkanInstruksi() {
        System.out.println(getInstruksi());
    }
}
//p