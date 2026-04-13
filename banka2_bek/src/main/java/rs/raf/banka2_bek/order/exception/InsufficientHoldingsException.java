package rs.raf.banka2_bek.order.exception;

/**
 * Baca se kada korisnik pokusava da proda vise hartija nego sto ima raspolozivo
 * (quantity - reservedQuantity).
 */
public class InsufficientHoldingsException extends RuntimeException {
    public InsufficientHoldingsException(String message) {
        super(message);
    }
}
