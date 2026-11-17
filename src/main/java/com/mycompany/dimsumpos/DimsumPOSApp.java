package com.mycompany.dimsumpos;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;

// Main class GUI JavaFX untuk DimsumPOS (Kuis Pertemuan 12)
public class DimsumPOSApp extends Application {

    // ---------- Data program (memakai class dari tugas sebelumnya) ----------
    private ArrayList<Menu> daftarMenu = new ArrayList<>();
    private ArrayList<Pesanan> daftarPesanan = new ArrayList<>();
    private int nomorBerikutnya = 1;
    private Pesanan pesananAktif = new Pesanan(nomorBerikutnya);
    private int totalPemasukan = 0;
    private int jumlahTransaksi = 0;

    // ---------- Komponen GUI yang perlu diakses di banyak method ----------
    private ListView<String> listMenu = new ListView<>();
    private TextField tfKode = new TextField();
    private TextField tfJumlah = new TextField();
    private TextArea taKeranjang = new TextArea();
    private Label lblTotal = new Label("Total: Rp0");
    private ComboBox<String> cbMetode = new ComboBox<>();
    private TextField tfNomor = new TextField();
    private TextArea taPesanan = new TextArea();
    private Label lblLaporan = new Label();

    @Override
    public void start(Stage stage) {
        // Data menu awal
        daftarMenu.add(new Menu("D01", "Dimsum Ayam", 15000, 20));
        daftarMenu.add(new Menu("D02", "Dimsum Udang", 18000, 20));
        daftarMenu.add(new Menu("D03", "Dimsum Mentai", 20000, 15));
        daftarMenu.add(new Menu("M01", "Es Teh", 5000, 30));
        daftarMenu.add(new Menu("M02", "Lemon Tea", 8000, 30));

        Tab tabPelanggan = new Tab("Pelanggan", buatTampilanPelanggan());
        Tab tabKasir = new Tab("Kasir", buatTampilanKasir());
        tabPelanggan.setClosable(false);
        tabKasir.setClosable(false);

        TabPane tabPane = new TabPane(tabPelanggan, tabKasir);

        refreshMenu();
        refreshDaftarPesanan();

        Scene scene = new Scene(tabPane, 900, 560);
        stage.setTitle("DimsumPOS");
        stage.setScene(scene);
        stage.show();
    }

    // =================== TAMPILAN PELANGGAN ===================
    private VBox buatTampilanPelanggan() {
        Label judul = new Label("DimsumPOS - Pesan Menu");
        judul.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        // --- Kolom kiri: daftar menu dan form input ---
        Label lblMenu = new Label("Daftar Menu (klik untuk memilih)");
        listMenu.setPrefHeight(230);

        // Klik menu di list -> kode otomatis terisi
        listMenu.getSelectionModel().selectedIndexProperty().addListener((obs, lama, baru) -> {
            int idx = baru.intValue();
            if (idx >= 0 && idx < daftarMenu.size()) {
                tfKode.setText(daftarMenu.get(idx).getKode());
            }
        });

        tfKode.setPromptText("Contoh: D01");
        tfJumlah.setPromptText("Contoh: 2");

        Button btnTambah = new Button("Tambah ke Keranjang");
        btnTambah.setOnAction(e -> tambahKeKeranjang());

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.add(new Label("Kode Menu"), 0, 0);
        form.add(tfKode, 1, 0);
        form.add(new Label("Jumlah"), 0, 1);
        form.add(tfJumlah, 1, 1);
        form.add(btnTambah, 1, 2);

        VBox kiri = new VBox(10, lblMenu, listMenu, form);
        kiri.setPrefWidth(430);

        // --- Kolom kanan: keranjang dan pembayaran ---
        Label lblKeranjang = new Label("Keranjang");
        taKeranjang.setEditable(false);
        taKeranjang.setPrefHeight(230);
        lblTotal.setStyle("-fx-font-weight: bold;");

        cbMetode.setItems(FXCollections.observableArrayList("QRIS", "Tunai"));
        cbMetode.setPromptText("Pilih metode pembayaran");

        Button btnBayar = new Button("Bayar");
        btnBayar.setOnAction(e -> prosesBayar());

        Button btnKosongkan = new Button("Kosongkan Keranjang");
        btnKosongkan.setOnAction(e -> {
            pesananAktif = new Pesanan(nomorBerikutnya);
            refreshKeranjang();
        });

        HBox barisBayar = new HBox(10, cbMetode, btnBayar, btnKosongkan);
        VBox kanan = new VBox(10, lblKeranjang, taKeranjang, lblTotal, barisBayar);
        HBox.setHgrow(kanan, Priority.ALWAYS);

        HBox isi = new HBox(20, kiri, kanan);

        VBox root = new VBox(15, judul, isi);
        root.setPadding(new Insets(20));
        return root;
    }

    // =================== TAMPILAN KASIR ===================
    private VBox buatTampilanKasir() {
        Label judul = new Label("DimsumPOS - Kasir");
        judul.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        tfNomor.setPromptText("Nomor pesanan, contoh: 1");

        Button btnKonfirmasi = new Button("Konfirmasi Pembayaran");
        btnKonfirmasi.setOnAction(e -> konfirmasiPembayaran());

        HBox barisKonfirmasi = new HBox(10, new Label("Nomor Pesanan"), tfNomor, btnKonfirmasi);

        Label lblDaftar = new Label("Pesanan Masuk");
        taPesanan.setEditable(false);
        VBox.setVgrow(taPesanan, Priority.ALWAYS);

        lblLaporan.setStyle("-fx-font-weight: bold;");

        VBox root = new VBox(12, judul, barisKonfirmasi, lblDaftar, taPesanan, lblLaporan);
        root.setPadding(new Insets(20));
        return root;
    }

    // =================== EVENT HANDLING ===================
    private void tambahKeKeranjang() {
        String kode = tfKode.getText().trim();
        String teksJumlah = tfJumlah.getText().trim();

        // Validasi: kolom kosong
        if (kode.isEmpty() || teksJumlah.isEmpty()) {
            tampilAlert(Alert.AlertType.WARNING, "Kolom belum lengkap",
                    "Kode menu dan jumlah wajib diisi!");
            return;
        }

        // Validasi: format angka dengan try-catch
        int jumlah;
        try {
            jumlah = Integer.parseInt(teksJumlah);
        } catch (NumberFormatException ex) {
            tampilAlert(Alert.AlertType.ERROR, "Format salah",
                    "Jumlah harus berupa angka!");
            return;
        }

        if (jumlah <= 0) {
            tampilAlert(Alert.AlertType.WARNING, "Jumlah tidak valid",
                    "Jumlah harus lebih dari 0.");
            return;
        }

        Menu menu = cariMenu(kode);
        if (menu == null) {
            tampilAlert(Alert.AlertType.ERROR, "Menu tidak ditemukan",
                    "Tidak ada menu dengan kode \"" + kode + "\".");
            return;
        }

        // Validasi stok (termasuk yang sudah ada di keranjang)
        int sudahDiKeranjang = jumlahDiKeranjang(menu);
        if (jumlah + sudahDiKeranjang > menu.getStok()) {
            tampilAlert(Alert.AlertType.WARNING, "Stok tidak cukup",
                    "Stok " + menu.getNama() + " tersisa " + menu.getStok() + ".");
            return;
        }

        pesananAktif.tambahItem(menu, jumlah);
        tfJumlah.clear();
        refreshKeranjang();
    }

    private void prosesBayar() {
        if (pesananAktif.kosong()) {
            tampilAlert(Alert.AlertType.WARNING, "Keranjang kosong",
                    "Tambahkan menu ke keranjang terlebih dahulu.");
            return;
        }

        String metode = cbMetode.getValue();
        if (metode == null) {
            tampilAlert(Alert.AlertType.WARNING, "Metode belum dipilih",
                    "Pilih metode pembayaran (QRIS atau Tunai).");
            return;
        }

        int total = pesananAktif.hitungTotal();
        Pembayaran bayar;
        if (metode.equals("QRIS")) {
            bayar = new PembayaranQRIS(total);
        } else {
            bayar = new PembayaranTunai(total);
        }

        pesananAktif.setPembayaran(bayar);
        daftarPesanan.add(pesananAktif);
        int nomor = pesananAktif.getNomor();

        // Siapkan pesanan baru untuk pelanggan berikutnya
        nomorBerikutnya++;
        pesananAktif = new Pesanan(nomorBerikutnya);
        cbMetode.setValue(null);

        refreshKeranjang();
        refreshDaftarPesanan();

        tampilAlert(Alert.AlertType.INFORMATION, "Pesanan #" + nomor + " dibuat",
                bayar.getInstruksi() + "\n\nStatus: MENUNGGU PEMBAYARAN\n"
                + "Mohon tunggu kasir mengonfirmasi pembayaran.");
    }

    private void konfirmasiPembayaran() {
        String teksNomor = tfNomor.getText().trim();
        if (teksNomor.isEmpty()) {
            tampilAlert(Alert.AlertType.WARNING, "Kolom belum diisi",
                    "Masukkan nomor pesanan terlebih dahulu!");
            return;
        }

        int nomor;
        try {
            nomor = Integer.parseInt(teksNomor);
        } catch (NumberFormatException ex) {
            tampilAlert(Alert.AlertType.ERROR, "Format salah",
                    "Nomor pesanan harus berupa angka!");
            return;
        }

        for (Pesanan p : daftarPesanan) {
            if (p.getNomor() == nomor) {
                if (p.getStatus().equals("LUNAS")) {
                    tampilAlert(Alert.AlertType.INFORMATION, "Sudah lunas",
                            "Pesanan #" + nomor + " sudah dikonfirmasi sebelumnya.");
                    return;
                }
                p.setStatus("LUNAS");
                for (ItemPesanan item : p.getDaftarItem()) {
                    item.getMenu().kurangiStok(item.getJumlah());
                }
                totalPemasukan += p.hitungTotal();
                jumlahTransaksi++;
                tfNomor.clear();

                refreshMenu();
                refreshDaftarPesanan();
                tampilAlert(Alert.AlertType.INFORMATION, "Pembayaran berhasil",
                        "Pesanan #" + nomor + " dikonfirmasi: LUNAS.\n"
                        + "[Notifikasi ke pelanggan] Pembayaran berhasil, pesanan sedang diproses.");
                return;
            }
        }

        tampilAlert(Alert.AlertType.ERROR, "Pesanan tidak ditemukan",
                "Tidak ada pesanan dengan nomor " + nomor + ".");
    }

    // =================== METHOD BANTU ===================
    private Menu cariMenu(String kode) {
        for (Menu m : daftarMenu) {
            if (m.getKode().equalsIgnoreCase(kode)) {
                return m;
            }
        }
        return null;
    }

    private int jumlahDiKeranjang(Menu menu) {
        for (ItemPesanan item : pesananAktif.getDaftarItem()) {
            if (item.getMenu().getKode().equals(menu.getKode())) {
                return item.getJumlah();
            }
        }
        return 0;
    }

    private void refreshMenu() {
        ArrayList<String> baris = new ArrayList<>();
        for (Menu m : daftarMenu) {
            baris.add(m.getKode() + "  |  " + m.getNama() + "  |  Rp" + m.getHarga()
                    + "  |  stok " + m.getStok());
        }
        listMenu.setItems(FXCollections.observableArrayList(baris));
    }

    private void refreshKeranjang() {
        StringBuilder sb = new StringBuilder();
        for (ItemPesanan item : pesananAktif.getDaftarItem()) {
            sb.append(item.getMenu().getNama()).append(" x").append(item.getJumlah())
              .append(" = Rp").append(item.getSubtotal()).append("\n");
        }
        taKeranjang.setText(sb.toString());
        lblTotal.setText("Total: Rp" + pesananAktif.hitungTotal());
    }

    private void refreshDaftarPesanan() {
        StringBuilder sb = new StringBuilder();
        if (daftarPesanan.isEmpty()) {
            sb.append("Belum ada pesanan masuk.");
        }
        for (Pesanan p : daftarPesanan) {
            sb.append("Pesanan #").append(p.getNomor()).append(" | ")
              .append(p.getPembayaran().getMetode()).append(" | Rp")
              .append(p.hitungTotal()).append(" | ").append(p.getStatus()).append("\n");
            for (ItemPesanan item : p.getDaftarItem()) {
                sb.append("   - ").append(item.getMenu().getNama())
                  .append(" x").append(item.getJumlah()).append("\n");
            }
            sb.append("\n");
        }
        taPesanan.setText(sb.toString());
        lblLaporan.setText("Laporan Keuangan  |  Transaksi lunas: " + jumlahTransaksi
                + "  |  Total pemasukan: Rp" + totalPemasukan);
    }

    private void tampilAlert(Alert.AlertType tipe, String judul, String pesan) {
        Alert alert = new Alert(tipe);
        alert.setTitle(judul);
        alert.setHeaderText(null);
        alert.setContentText(pesan);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
