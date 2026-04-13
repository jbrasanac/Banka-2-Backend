package rs.raf.banka2_bek.order.exception;

/**
 * Baca se kada racun nema dovoljno raspolozivih sredstava za rezervaciju ordera.
 */
public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}
