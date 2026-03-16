package rs.raf.banka2_bek.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rs.raf.banka2_bek.payment.model.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
}

