package kasir;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Algoritma pengurutan & pencarian buatan sendiri.
 * Semua method di sini murni (tidak menyentuh UI/file) sehingga mudah diuji.
 */
public final class Algoritma {
    private Algoritma() { }

    /** Samakan perbandingan teks (abaikan huruf besar/kecil); angka apa adanya. */
    public static Object kunciUrut(Object nilai) {
        return nilai instanceof String ? ((String) nilai).toLowerCase(Locale.ROOT) : nilai;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int banding(Object a, Object b) {
        return ((Comparable) a).compareTo(b);
    }

    /** Merge sort urut naik menurut urutan alami elemen. */
    public static <T> List<T> mergeSort(List<T> data) {
        return mergeSort(data, x -> x, false);
    }

    /**
     * Merge sort (stabil), kompleksitas O(n log n).
     * Data tidak diubah, hasil berupa list baru.
     *
     * @param key     fungsi pengambil nilai pembanding
     * @param reverse true untuk urut menurun
     */
    public static <T> List<T> mergeSort(List<T> data, Function<? super T, ?> key, boolean reverse) {
        if (key == null) {
            throw new AssertionError("key harus berupa fungsi");
        }
        if (data.size() <= 1) {
            return new ArrayList<>(data);
        }
        int tengah = data.size() / 2;
        List<T> kiri = mergeSort(data.subList(0, tengah), key, reverse);
        List<T> kanan = mergeSort(data.subList(tengah, data.size()), key, reverse);
        return gabung(kiri, kanan, key, reverse);
    }

    /** Gabungkan dua list yang sudah terurut menjadi satu list terurut. */
    private static <T> List<T> gabung(List<T> kiri, List<T> kanan, Function<? super T, ?> key, boolean reverse) {
        List<T> hasil = new ArrayList<>(kiri.size() + kanan.size());
        int i = 0;
        int j = 0;
        while (i < kiri.size() && j < kanan.size()) {
            int c = banding(key.apply(kiri.get(i)), key.apply(kanan.get(j)));
            // '>=' / '<=' (bukan '>' / '<') menjaga urutan elemen sama tetap stabil
            boolean ambilKiri = reverse ? c >= 0 : c <= 0;
            if (ambilKiri) {
                hasil.add(kiri.get(i++));
            } else {
                hasil.add(kanan.get(j++));
            }
        }
        while (i < kiri.size()) {
            hasil.add(kiri.get(i++));
        }
        while (j < kanan.size()) {
            hasil.add(kanan.get(j++));
        }
        return hasil;
    }

    /** Binary search O(log n) pada data yang SUDAH terurut naik. Mengembalikan indeks, atau -1. */
    public static <T> int binarySearch(List<T> data, Object target, Function<? super T, ?> key) {
        int kiri = 0;
        int kanan = data.size() - 1;
        while (kiri <= kanan) {
            int tengah = (kiri + kanan) / 2;
            int c = banding(key.apply(data.get(tengah)), target);
            if (c == 0) {
                return tengah;
            }
            if (c < 0) {
                kiri = tengah + 1;
            } else {
                kanan = tengah - 1;
            }
        }
        return -1;
    }

    public static <T extends Comparable<T>> int binarySearch(List<T> data, T target) {
        return binarySearch(data, target, x -> x);
    }

    /** Pencarian kata kunci (substring, tidak peka huruf besar/kecil). Kata kosong = semua data cocok. */
    public static <T> List<T> cariLinear(List<T> data, String kata, Function<? super T, String> ambilTeks) {
        String k = kata.strip().toLowerCase(Locale.ROOT);
        if (k.isEmpty()) {
            return new ArrayList<>(data);
        }
        List<T> hasil = new ArrayList<>();
        for (T d : data) {
            if (ambilTeks.apply(d).toLowerCase(Locale.ROOT).contains(k)) {
                hasil.add(d);
            }
        }
        return hasil;
    }
}
