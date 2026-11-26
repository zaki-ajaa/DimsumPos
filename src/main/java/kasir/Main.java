package kasir;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Titik masuk aplikasi UniCashier Dimsum Mentai (versi Java).
 * Jalankan: java -cp out kasir.Main   (browser terbuka otomatis)
 */
public class Main {

    /** Cari folder proyek (yang berisi folder `static`), mulai dari folder kerja lalu lokasi kelas. */
    static Path cariFolderProyek() {
        List<Path> awal = new java.util.ArrayList<>();
        awal.add(Path.of(System.getProperty("user.dir")).toAbsolutePath());
        try {
            awal.add(Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath());
        } catch (URISyntaxException | RuntimeException e) {
            // lokasi kelas tidak diketahui: pakai folder kerja saja
        }
        for (Path mulai : awal) {
            Path p = mulai;
            for (int naik = 0; naik < 5 && p != null; naik++) {
                if (Files.isDirectory(p.resolve("static"))) {
                    return p;
                }
                p = p.getParent();
            }
        }
        throw new IllegalStateException("Folder 'static' tidak ditemukan. Jalankan dari folder proyek.");
    }

    public static void main(String[] args) {
        Path folder = cariFolderProyek();
        TokoService service = new TokoService(new Storage(folder.resolve("data")));
        new AplikasiWeb(service, folder.resolve("static")).jalankan(true);
    }
}
