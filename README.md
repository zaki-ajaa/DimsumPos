# DimsumPOS

Aplikasi kasir (POS) berbasis console untuk toko dimsum — Tugas 4 Pemrograman Berorientasi Objek (PBO).

## Fitur
- Mode Pelanggan: pilih menu, atur jumlah, lihat keranjang, pilih pembayaran (QRIS / Tunai)
- Mode Kasir: lihat pesanan masuk, konfirmasi pembayaran, laporan keuangan

## Materi PBO yang diterapkan
| Materi | Implementasi |
|---|---|
| Scanner & Control Flow | `Main.java` (Scanner, `while`, `for`, `switch`, `if/else`) |
| Class & Object | `Menu`, `ItemPesanan`, `Pesanan`, `Pembayaran`; objek dibuat di `main` |
| Encapsulation | atribut `private` + getter/setter (`Menu`, `Pesanan`, `Pembayaran`) |
| Inheritance | `PembayaranQRIS` & `PembayaranTunai` extends `Pembayaran` |

## Cara menjalankan
```bash
javac -d out src/main/java/com/mycompany/dimsumpos/*.java
java -cp out com.mycompany.dimsumpos.Main
```
Atau buka folder ini di IDE (NetBeans / IntelliJ / VS Code) lalu run `Main.java`.
