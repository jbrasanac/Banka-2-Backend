package rs.raf.banka2_bek.loan.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import rs.raf.banka2_bek.loan.model.LoanRequest;
import rs.raf.banka2_bek.loan.model.LoanStatus;

public interface LoanRequestRepository extends JpaRepository<LoanRequest, Long> {

    Page<LoanRequest> findByStatus(LoanStatus status, Pageable pageable);

    Page<LoanRequest> findByClientId(Long clientId, Pageable pageable);
}
