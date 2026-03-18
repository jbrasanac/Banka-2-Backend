package rs.raf.banka2_bek.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import rs.raf.banka2_bek.payment.model.Payment;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	@Query("select p from Payment p " +
		   "where p.fromAccount.client.id = :clientId " +
		   "or p.toAccountNumber in (select a.accountNumber from Account a where a.client.id = :clientId)")
	Page<Payment> findByUserAccounts(@Param("clientId") Long clientId, Pageable pageable);
}

