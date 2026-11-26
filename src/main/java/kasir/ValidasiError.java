package kasir;

/**
 * Error khusus untuk input yang tidak valid.
 * Turunan IllegalArgumentException (padanan ValueError di Python).
 */
public class ValidasiError extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public ValidasiError(String pesan) {
        super(pesan);
    }
}
