package rs.raf.banka2_bek.transaction.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.payment.model.Payment;
import rs.raf.banka2_bek.transfer.model.Transfer;

/**
 * Imutabilni zapis svake finansijske operacije na računu.
 * Jednom kreirana transakcija se nikad ne menja niti briše.
 * Za korekcije se kreira storno transakcija (sa negativnim vrednostima).
 */
@Entity
@Table(name = "transactions")
@Immutable   // Hibernate — sprečava slučajne UPDATE-ove
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Račun na koji se transakcija knjiži
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "currency_id", nullable = false, updatable = false)
    private Currency currency;

    // Ko je inicirao transakciju — tačno jedno od ova dva mora biti postavljeno
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", updatable = false)
    private User client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", updatable = false)
    private Employee employee;

    // Vezano za plaćanje (opciono)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", updatable = false)
    private Payment payment;

    // Vezano za prenos / menjačnicu (opciono)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", updatable = false)
    private Transfer transfer;

    @Column(length = 255, updatable = false)
    private String description;

    // ── Finansijski podaci ────────────────────────────────────────────────────

    // Isplata — iznos koji se skida sa računa (0 ako nema isplate)
    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    @Builder.Default
    private BigDecimal debit = BigDecimal.ZERO;

    // Uplata — iznos koji se dodaje na račun (0 ako nema uplate)
    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    @Builder.Default
    private BigDecimal credit = BigDecimal.ZERO;

    // Rezervisana sredstva (za buduće kupovine hartija od vrednosti)
    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    @Builder.Default
    private BigDecimal reserved = BigDecimal.ZERO;

    // Koliko rezervisanih sredstava se koristi u ovoj transakciji
    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    @Builder.Default
    private BigDecimal reservedUsed = BigDecimal.ZERO;

    // Stanje na računu NAKON ove transakcije (snapshot za brz prikaz)
    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal balanceAfter;

    // Raspoloživo stanje NAKON ove transakcije
    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal availableAfter;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
