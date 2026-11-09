package com.mycompany.dimsumpos;

public class PembayaranQRIS extends Pembayaran {

    public PembayaranQRIS(int totalBayar) {
        super(totalBayar, "QRIS");
    }

    @Override
    public void tampilkanInstruksi() {
        System.out.println("+-----------------------------+");
        System.out.println("|                             |");
        System.out.println("|                             |");
        System.out.println("|                             |");
        System.out.println("|                             |");
        System.out.println("+-----------------------------+");
        System.out.println("Total yang dibayar: Rp" + getTotalBayar());
    }
}
