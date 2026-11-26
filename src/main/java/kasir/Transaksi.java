package kasir;

import java.util.LinkedHashMap;
import java.util.Map;

/** Satu transaksi penjualan yang sudah dibayar. */
public class Transaksi {
    private final String id;
    private final String waktu;  // format "YYYY-MM-DD HH:MM"
    private final String items;  // contoh: "Mentai Original x2; Es Teh x1"
    private final int subtotal;
    private final int diskon;    // nominal diskon (Rp)
    private final int total;
    private final int hpp;       // total modal barang yang terjual
    private final String metode;

    public Transaksi(String id, String waktu, String items, int subtotal,
                     int diskon, int total, int hpp, String metode) {
        this.id = id;
        this.waktu = waktu;
        this.items = items;
        this.subtotal = subtotal;
        this.diskon = diskon;
        this.total = total;
        this.hpp = hpp;
        this.metode = metode;
    }

    public String getId() { return id; }
    public String getWaktu() { return waktu; }
    public String getItems() { return items; }
    public int getSubtotal() { return subtotal; }
    public int getDiskon() { return diskon; }
    public int getTotal() { return total; }
    public int getHpp() { return hpp; }
    public String getMetode() { return metode; }

    /** Laba kotor transaksi = total - HPP. */
    public int getLaba() {
        return total - hpp;
    }

    public Map<String, Object> keMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("waktu", waktu);
        m.put("items", items);
        m.put("subtotal", subtotal);
        m.put("diskon", diskon);
        m.put("total", total);
        m.put("hpp", hpp);
        m.put("metode", metode);
        return m;
    }
}
