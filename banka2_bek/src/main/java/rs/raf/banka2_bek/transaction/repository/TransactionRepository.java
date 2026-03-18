package rs.raf.banka2_bek.transaction.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import rs.raf.banka2_bek.transaction.model.Transaction;

import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
	Page<Transaction> findByAccountClientId(Long clientId, Pageable pageable);
}
