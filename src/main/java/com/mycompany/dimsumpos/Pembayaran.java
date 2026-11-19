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
=======

>>>>>>> 3334e400207322365a5c9b78bb29cb12098b6fc4

    // Teks instruksi pembayaran (dipakai oleh tampilan GUI)
    public String getInstruksi() {
        return "Silakan lakukan pembayaran sebesar Rp" + totalBayar;
    }

<<<<<<< HEAD
=======
    // Versi terminal, ditimpa (override) oleh subclass
>>>>>>> 3334e400207322365a5c9b78bb29cb12098b6fc4

    public void tampilkanInstruksi() {
        System.out.println(getInstruksi());
    }
}
//p
