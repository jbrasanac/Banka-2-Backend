package rs.raf.banka2_bek.order.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import rs.raf.banka2_bek.stock.model.Listing;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Nalog za kupovinu ili prodaju hartije od vrednosti.
 *
 * Specifikacija: Celina 3 - Order
 */
@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_role", nullable = false)
    private String userRole; // "EMPLOYEE" ili "CLIENT"

    @ManyToOne
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType orderType;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "contract_size", nullable = false)
    private Integer contractSize;

    @Column(name = "price_per_unit", precision = 18, scale = 4)
    private BigDecimal pricePerUnit;

    @Column(name = "limit_value", precision = 18, scale = 4)
    private BigDecimal limitValue;

    @Column(name = "stop_value", precision = 18, scale = 4)
    private BigDecimal stopValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "is_done", nullable = false)
    private boolean isDone;

    @Column(name = "last_modification")
    private LocalDateTime lastModification;

    @Column(name = "remaining_portions")
    private Integer remainingPortions;

    @Column(name = "after_hours", nullable = false)
    private boolean afterHours;

    @Column(name = "all_or_none", nullable = false)
    private boolean allOrNone;

    @Column(name = "is_margin", nullable = false)
    private boolean margin;

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "approximate_price", precision = 18, scale = 4)
    private BigDecimal approximatePrice;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // ── Rezervacija sredstava ─────────────────────────────────────────────
    @Column(name = "reserved_account_id")
    private Long reservedAccountId;

    @Column(name = "reserved_amount", precision = 19, scale = 4)
    private BigDecimal reservedAmount;

    @Column(name = "reservation_released", nullable = false)
    private boolean reservationReleased = false;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** Kurs u trenutku rezervacije (listing currency -> account currency). */
    @Column(name = "exchange_rate", precision = 19, scale = 6)
    private BigDecimal exchangeRate;
}
