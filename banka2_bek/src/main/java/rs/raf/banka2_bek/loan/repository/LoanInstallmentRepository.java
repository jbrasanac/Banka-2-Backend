package rs.raf.banka2_bek.loan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rs.raf.banka2_bek.loan.model.LoanInstallment;

import java.util.List;

public interface LoanInstallmentRepository extends JpaRepository<LoanInstallment, Long> {

    List<LoanInstallment> findByLoanIdOrderByExpectedDueDateAsc(Long loanId);
}
