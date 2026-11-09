package com.mycompany.dimsumpos;

import java.util.ArrayList;

public class Pesanan {
    private int nomor;
    private ArrayList<ItemPesanan> daftarItem = new ArrayList<>();
    private String status;
    private Pembayaran pembayaran;

    public Pesanan(int nomor) {
        this.nomor = nomor;
        this.status = "MENUNGGU PEMBAYARAN";
    }

    public int getNomor() { return nomor; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Pembayaran getPembayaran() { return pembayaran; }
    public void setPembayaran(Pembayaran pembayaran) { this.pembayaran = pembayaran; }
    public ArrayList<ItemPesanan> getDaftarItem() { return daftarItem; }

    public void tambahItem(Menu menu, int jumlah) {
        for (ItemPesanan item : daftarItem) {
            if (item.getMenu().getKode().equals(menu.getKode())) {
                item.tambahJumlah(jumlah);
                return;
            }
        }
        daftarItem.add(new ItemPesanan(menu, jumlah));
    }

    public boolean kosong() {
        return daftarItem.isEmpty();
    }

    public int hitungTotal() {
        int total = 0;
        for (ItemPesanan item : daftarItem) {
            total += item.getSubtotal();
        }
        return total;
    }
}
