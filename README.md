# UniCashier Dimsum Mentai (Java + HTML/CSS/JS)

Aplikasi kasir untuk UMKM dimsum mentai. **Versi Java** dari proyek Python sebelumnya.
Tampilan **tidak diubah** (folder `static/`: HTML/CSS/JS yang sama). Semua logika berjalan di **Java**
(tanpa JavaFX dan tanpa library tambahan) dan dilayani oleh server lokal `com.sun.net.httpserver` bawaan JDK.

## Menjalankan
Butuh JDK 17 atau lebih baru. Dari folder proyek:
```
sh run.sh          # macOS/Linux  -> browser terbuka di http://127.0.0.1:8000
run.bat            # Windows
sh run.sh tes      # jalankan 18 unit test
```
Atau manual:
```
javac -d out $(find src -name '*.java')
java -cp out kasir.Main
java -cp out kasir.TestKasir
```
Data tersimpan di folder `data/` (`produk.csv`, `transaksi.csv`), dibuat otomatis saat pertama jalan.
Format CSV sama dengan versi Python, jadi file `data/` lama masih bisa dipakai.

Di NetBeans/VS Code: buka folder ini, lalu jalankan class `kasir.Main` (jalankan dari folder proyek agar folder `static` ditemukan).

## Struktur
| Berkas (`src/main/java/kasir/`) | Isi | Padanan Python |
|---|---|---|
| `Main.java` | titik masuk | `main.py` |
| `AplikasiWeb.java` | server HTTP + rute JSON API + berkas statis | `web.py` |
| `TokoService.java` | `TokoService` (produk, checkout, laporan), `hitungTotal(long...)`, `filterProduk(Map)` | `services.py` |
| `Storage.java` | baca/tulis CSV (mode r/w/a/x lewat `Files` + `StandardOpenOption`) | `storage.py` |
| `Produk.java`, `Transaksi.java`, `Keranjang.java` | kelas data | `models.py` |
| `Algoritma.java` | merge sort, binary search, pencarian linear (buatan sendiri) | `algoritma.py` |
| `Validasi.java`, `ValidasiError.java` | validasi input, `throw new ValidasiError` | `validasi.py` |
| `Json.java` | baca/tulis JSON sederhana (pengganti modul `json`) | `json` |
| `src/test/java/kasir/TestKasir.java` | 18 test (3 kelompok) | `tests/test_kasir.py` |

## Alur
Browser (JS) -> `fetch('/api/...')` -> `AplikasiWeb` -> `TokoService` -> `Storage` (CSV).
Sorting, searching, validasi, dan perhitungan transaksi dilakukan di Java; JS hanya menampilkan.

## Pemetaan konsep Python -> Java
| Python | Java |
|---|---|
| `@dataclass Produk` | class `Produk` (field private + getter/setter) |
| `*args` di `hitung_total` | varargs `hitungTotal(long... angka)` |
| `**kwargs` + `lambda` di `filter_produk` | `Map<String,Object>` + `BiPredicate` di `ATURAN_FILTER` |
| `raise ValidasiError` (turunan `ValueError`) | `throw new ValidasiError` (turunan `IllegalArgumentException`) |
| `try/except FileNotFoundError` | `catch (NoSuchFileException)` |
| `open(..., "x")` / `"a"` / `"w"` | `CREATE_NEW` / `APPEND` / `TRUNCATE_EXISTING` |
| `assert callable(key)` | pemeriksaan `key == null` -> `AssertionError` |
| `http.server` | `com.sun.net.httpserver.HttpServer` |
