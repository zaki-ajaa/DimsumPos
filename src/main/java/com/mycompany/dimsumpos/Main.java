package com.mycompany.dimsumpos;

import java.util.ArrayList;
import java.util.Scanner;

// Main class: menjalankan program DimsumPOS
public class Main {

    private static Scanner input = new Scanner(System.in);
    private static ArrayList<Menu> daftarMenu = new ArrayList<>();
    private static ArrayList<Pesanan> daftarPesanan = new ArrayList<>();
    private static int nomorTerakhir = 0;
    private static int totalPemasukan = 0;
    private static int jumlahTransaksi = 0;

    // Membaca angka dari Scanner, mengulang jika input bukan angka
    static int bacaAngka(String pesan) {
        while (true) {
            System.out.print(pesan);
            String teks = input.nextLine().trim();
            try {
                return Integer.parseInt(teks);
            } catch (NumberFormatException e) {
                System.out.println("Input harus berupa angka, coba lagi.");
            }
        }
    }

    static Menu cariMenu(String kode) {
        for (Menu m : daftarMenu) {
            if (m.getKode().equalsIgnoreCase(kode)) {
                return m;
            }
        }
        return null;
    }

    static void tampilkanMenu() {
        System.out.println("\n=========== DAFTAR MENU ===========");
        System.out.printf("%-5s %-22s %-8s %s%n", "Kode", "Nama", "Harga", "Stok");
        for (Menu m : daftarMenu) {
            System.out.printf("%-5s %-22s %-8d %d%n",
                    m.getKode(), m.getNama(), m.getHarga(), m.getStok());
        }
    }

    static void tampilkanKeranjang(Pesanan p) {
        System.out.println("\n--------- KERANJANG ---------");
        for (ItemPesanan item : p.getDaftarItem()) {
            System.out.println(item.getMenu().getNama() + " x" + item.getJumlah()
                    + " = Rp" + item.getSubtotal());
        }
        System.out.println("TOTAL: Rp" + p.hitungTotal());
    }

    // ===================== MODE PELANGGAN =====================
    static void modePelanggan() {
        nomorTerakhir++;
        Pesanan pesanan = new Pesanan(nomorTerakhir);
        boolean memilih = true;

        while (memilih) {
            tampilkanMenu();
            System.out.println("\n1. Tambah menu ke keranjang");
            System.out.println("2. Lihat keranjang");
            System.out.println("3. Selesai dan bayar");
            System.out.println("0. Batal");
            int pilihan = bacaAngka("Pilih: ");

            switch (pilihan) {
                case 1:
                    System.out.print("Masukkan kode menu: ");
                    String kode = input.nextLine().trim();
                    Menu menu = cariMenu(kode);
                    if (menu == null) {
                        System.out.println("Menu tidak ditemukan.");
                        break;
                    }
                    int jumlah = bacaAngka("Jumlah: ");
                    if (jumlah <= 0) {
                        System.out.println("Jumlah harus lebih dari 0.");
                    } else if (jumlah > menu.getStok()) {
                        System.out.println("Stok tidak cukup. Sisa stok: " + menu.getStok());
                    } else {
                        pesanan.tambahItem(menu, jumlah);
                        System.out.println(menu.getNama() + " x" + jumlah + " masuk keranjang.");
                    }
                    break;
                case 2:
                    if (pesanan.kosong()) {
                        System.out.println("Keranjang masih kosong.");
                    } else {
                        tampilkanKeranjang(pesanan);
                    }
                    break;
                case 3:
                    if (pesanan.kosong()) {
                        System.out.println("Keranjang masih kosong.");
                    } else {
                        prosesPembayaran(pesanan);
                        memilih = false;
                    }
                    break;
                case 0:
                    System.out.println("Pesanan dibatalkan.");
                    nomorTerakhir--;
                    memilih = false;
                    break;
                default:
                    System.out.println("Pilihan tidak valid.");
            }
        }
    }

    static void prosesPembayaran(Pesanan pesanan) {
        tampilkanKeranjang(pesanan);
        System.out.println("\nPilih metode pembayaran:");
        System.out.println("1. QRIS");
        System.out.println("2. Tunai");
        int metode = bacaAngka("Pilih: ");

        int total = pesanan.hitungTotal();
        Pembayaran bayar;
        if (metode == 1) {
            bayar = new PembayaranQRIS(total);
        } else {
            bayar = new PembayaranTunai(total);
        }
        pesanan.setPembayaran(bayar);
        daftarPesanan.add(pesanan);

        System.out.println("\nNomor pesanan Anda: #" + pesanan.getNomor());
        bayar.tampilkanInstruksi();
        System.out.println("Status: " + pesanan.getStatus());
        System.out.println("Menunggu kasir mengonfirmasi pembayaran...");
    }

    // ======================= MODE KASIR =======================
    static void modeKasir() {
        boolean aktif = true;
        while (aktif) {
            System.out.println("\n=========== MENU KASIR ===========");
            System.out.println("1. Lihat pesanan masuk");
            System.out.println("2. Konfirmasi pembayaran");
            System.out.println("3. Laporan keuangan");
            System.out.println("0. Kembali");
            int pilihan = bacaAngka("Pilih: ");

            switch (pilihan) {
                case 1:
                    lihatPesanan();
                    break;
                case 2:
                    konfirmasiPembayaran();
                    break;
                case 3:
                    laporanKeuangan();
                    break;
                case 0:
                    aktif = false;
                    break;
                default:
                    System.out.println("Pilihan tidak valid.");
            }
        }
    }

    static void lihatPesanan() {
        if (daftarPesanan.isEmpty()) {
            System.out.println("Belum ada pesanan masuk.");
            return;
        }
        for (Pesanan p : daftarPesanan) {
            System.out.println("\nPesanan #" + p.getNomor() + " | "
                    + p.getPembayaran().getMetode() + " | Rp" + p.hitungTotal()
                    + " | " + p.getStatus());
            for (ItemPesanan item : p.getDaftarItem()) {
                System.out.println("  - " + item.getMenu().getNama() + " x" + item.getJumlah());
            }
        }
    }

    static void konfirmasiPembayaran() {
        int nomor = bacaAngka("Masukkan nomor pesanan: ");
        for (Pesanan p : daftarPesanan) {
            if (p.getNomor() == nomor) {
                if (p.getStatus().equals("LUNAS")) {
                    System.out.println("Pesanan ini sudah lunas.");
                    return;
                }
                p.setStatus("LUNAS");
                for (ItemPesanan item : p.getDaftarItem()) {
                    item.getMenu().kurangiStok(item.getJumlah());
                }
                totalPemasukan += p.hitungTotal();
                jumlahTransaksi++;
                System.out.println("Pembayaran pesanan #" + nomor + " dikonfirmasi: LUNAS.");
                System.out.println("[Notifikasi ke pelanggan] Pembayaran berhasil! Pesanan #"
                        + nomor + " sedang diproses.");
                return;
            }
        }
        System.out.println("Pesanan tidak ditemukan.");
    }

    static void laporanKeuangan() {
        System.out.println("\n======== LAPORAN KEUANGAN ========");
        System.out.println("Jumlah transaksi lunas : " + jumlahTransaksi);
        System.out.println("Total pemasukan        : Rp" + totalPemasukan);
    }

    // ========================= MAIN =========================
    public static void main(String[] args) {
        // Data menu awal (object dibuat dari class Menu)
        daftarMenu.add(new Menu("D01", "Dimsum Ayam", 15000, 20));
        daftarMenu.add(new Menu("D02", "Dimsum Udang", 18000, 20));
        daftarMenu.add(new Menu("D03", "Dimsum Mentai", 20000, 15));
        daftarMenu.add(new Menu("M01", "Es Teh", 5000, 30));
        daftarMenu.add(new Menu("M02", "Lemon Tea", 8000, 30));

        boolean jalan = true;
        while (jalan) {
            System.out.println("\n=========== DIMSUMPOS ===========");
            System.out.println("1. Mulai pesan (Pelanggan)");
            System.out.println("2. Menu kasir");
            System.out.println("0. Keluar");
            int pilihan = bacaAngka("Pilih: ");

            if (pilihan == 1) {
                modePelanggan();
            } else if (pilihan == 2) {
                modeKasir();
            } else if (pilihan == 0) {
                System.out.println("Terima kasih!");
                jalan = false;
            } else {
                System.out.println("Pilihan tidak valid.");
            }
        }
    }
}
