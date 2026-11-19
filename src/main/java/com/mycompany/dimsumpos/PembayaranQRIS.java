package com.mycompany.dimsumpos;

public class PembayaranQRIS extends Pembayaran {

    public PembayaranQRIS(int totalBayar) {
        super(totalBayar, "QRIS");
    }

    @Override
    public String getInstruksi() {
        return "Scan kode QRIS (simulasi) untuk membayar Rp" + totalBayar;
    }

    @Override
    public void tampilkanInstruksi() {
        System.out.println("+-----------------------------+");
        System.out.println("|                             |");
        System.out.println("|    Qris akan ditampilkan    |");
        System.out.println("|        petugas kasir        |");
        System.out.println("|                             |");
        System.out.println("+-----------------------------+");
        System.out.println("Total yang dibayar: Rp" + totalBayar);
    }
}
//p