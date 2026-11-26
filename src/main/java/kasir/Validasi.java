package kasir;

import java.util.List;
import java.util.Locale;

/** Aturan validasi input. Murni logika (tanpa UI) sehingga mudah diuji. */
public final class Validasi {
    private Validasi() { }

    // Pilihan tetap yang dipakai di seluruh aplikasi
    public static final List<String> KATEGORI = List.of("Dimsum", "Topping", "Minuman", "Paket");
    public static final List<String> METODE = List.of("Tunai", "QRIS", "Transfer");

    /** Ubah nilai menjadi int; lempar ValidasiError bila bukan angka bulat. */
    public static int keInt(Object nilai, String namaField) {
        try {
            return Integer.parseInt(String.valueOf(nilai).strip());
        } catch (NumberFormatException e) {
            throw new ValidasiError(namaField + " harus berupa angka bulat");
        }
    }

    /**
     * Validasi satu data produk dan kembalikan Produk yang sudah dibersihkan.
     * Aturan: SKU & nama wajib diisi, kategori harus dikenal, harga/HPP/stok
     * tidak boleh negatif, HPP tidak boleh melebihi harga jual.
     */
    public static Produk validasiProduk(Object sku, Object nama, Object kategori,
                                        Object harga, Object hpp, Object stok) {
        String skuBersih = String.valueOf(sku).strip().toUpperCase(Locale.ROOT);
        String namaBersih = String.valueOf(nama).strip();
        if (skuBersih.isEmpty()) {
            throw new ValidasiError("SKU wajib diisi");
        }
        if (namaBersih.isEmpty()) {
            throw new ValidasiError("Nama produk wajib diisi");
        }
        if (!KATEGORI.contains(kategori)) {
            throw new ValidasiError("Kategori tidak valid");
        }

        int h = keInt(harga, "Harga");
        int m = keInt(hpp, "HPP");
        int s = keInt(stok, "Stok");
        if (h < 0) {
            throw new ValidasiError("Harga tidak boleh negatif");
        }
        if (m < 0) {
            throw new ValidasiError("HPP tidak boleh negatif");
        }
        if (s < 0) {
            throw new ValidasiError("Stok tidak boleh negatif");
        }
        if (m > h) {
            throw new ValidasiError("HPP tidak boleh melebihi harga jual");
        }
        return new Produk(skuBersih, namaBersih, (String) kategori, h, m, s);
    }
}
