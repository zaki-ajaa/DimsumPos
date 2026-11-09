package com.mycompany.dimsumpos;

// Subclass PembayaranTunai: mewarisi sifat dari Pembayaran
public class PembayaranTunai extends Pembayaran {

    public PembayaranTunai(int totalBayar) {
        super(totalBayar, "TUNAI");
    }

    @Override
    public void tampilkanInstruksi() {
        System.out.println("Silakan bayar tunai ke kasir sebesar Rp" + getTotalBayar());
    }
}
