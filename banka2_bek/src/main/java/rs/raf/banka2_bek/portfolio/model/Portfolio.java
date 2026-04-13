package rs.raf.banka2_bek.portfolio.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entitet za portfolio — predstavlja kolicinu hartija od vrednosti
 * koje korisnik poseduje.
 */
@Entity
@Table(name = "portfolios")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID korisnika (users tabela) koji poseduje hartije. */
    @Column(nullable = false)
    private Long userId;

    /** ID listinga (listings tabela) — hartija od vrednosti. */
    @Column(nullable = false)
    private Long listingId;

    /** Ticker hartije — cache za brzi prikaz. */
    @Column(nullable = false, length = 20)
    private String listingTicker;

    /** Naziv hartije — cache za brzi prikaz. */
    @Column(nullable = false)
    private String listingName;

    /** Tip hartije (STOCK, FUTURES, FOREX). */
    @Column(nullable = false, length = 20)
    private String listingType;

    /** Kolicina hartija u posedu. */
    @Column(nullable = false)
    private Integer quantity;

    /** Prosecna cena kupovine po jedinici. */
    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal averageBuyPrice;

    /** Broj hartija koje su javno vidljive za OTC trgovinu. */
    @Column(nullable = false)
    private Integer publicQuantity = 0;

    /** Rezervisana kolicina (za pending SELL ordere). Default 0. */
    @Column(nullable = false)
    private Integer reservedQuantity = 0;

    /** Datum poslednje izmene. */
    @Column(nullable = false)
    private LocalDateTime lastModified;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.lastModified = LocalDateTime.now();
    }

    /**
     * Kolicina koja je slobodna za novu prodaju (quantity minus reservedQuantity).
     * Nije persistirano — izracunava se na osnovu postojecih polja.
     */
    @Transient
    public Integer getAvailableQuantity() {
        return quantity - (reservedQuantity == null ? 0 : reservedQuantity);
    }
}
