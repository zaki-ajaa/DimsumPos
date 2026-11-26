package kasir;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/** Keranjang belanja kasir. Menyimpan {sku -> (Produk, qty)}. */
public class Keranjang {

    /** Satu baris keranjang. */
    public static class Item {
        public final Produk produk;
        public int qty;

        Item(Produk produk, int qty) {
            this.produk = produk;
            this.qty = qty;
        }
    }

    private final Map<String, Item> item = new LinkedHashMap<>();

    /** Qty produk `sku` di keranjang (0 jika belum ada). */
    public int jumlah(String sku) {
        Item i = item.get(sku);
        return i == null ? 0 : i.qty;
    }

    /** Tambah produk; lempar ValidasiError bila qty <= 0 atau melebihi stok. */
    public void tambah(Produk produk, int qty) {
        if (qty <= 0) {
            throw new ValidasiError("Jumlah harus lebih dari 0");
        }
        int baru = jumlah(produk.getSku()) + qty;
        if (baru > produk.getStok()) {
            throw new ValidasiError("Stok " + produk.getNama() + " hanya " + produk.getStok());
        }
        item.put(produk.getSku(), new Item(produk, baru));
    }

    /** Kurangi qty satu; hapus baris bila qty menjadi 0. */
    public void kurangi(String sku) {
        Item i = item.get(sku);
        if (i != null) {
            i.qty -= 1;
            if (i.qty <= 0) {
                item.remove(sku);
            }
        }
    }

    public void hapus(String sku) {
        item.remove(sku);
    }

    public void kosongkan() {
        item.clear();
    }

    public boolean kosong() {
        return item.isEmpty();
    }

    /** Daftar isi keranjang (salinan). */
    public List<Item> daftar() {
        return new ArrayList<>(item.values());
    }

    public int subtotal() {
        int total = 0;
        for (Item i : item.values()) {
            total += i.produk.getHarga() * i.qty;
        }
        return total;
    }

    public int hppTotal() {
        int total = 0;
        for (Item i : item.values()) {
            total += i.produk.getHpp() * i.qty;
        }
        return total;
    }

    /** Hitung {subtotal, diskonRupiah, total}. Diskon harus 0-100 persen. */
    public int[] hitung(int diskonPersen) {
        if (diskonPersen < 0 || diskonPersen > 100) {
            throw new ValidasiError("Diskon harus antara 0 sampai 100 persen");
        }
        int sub = subtotal();
        int diskon = sub * diskonPersen / 100;
        return new int[] {sub, diskon, sub - diskon};
    }

    /** Teks ringkas isi keranjang untuk disimpan di riwayat transaksi. */
    public String ringkasItem() {
        StringJoiner sj = new StringJoiner("; ");
        for (Item i : item.values()) {
            sj.add(i.produk.getNama() + " x" + i.qty);
        }
        return sj.toString();
    }
}
