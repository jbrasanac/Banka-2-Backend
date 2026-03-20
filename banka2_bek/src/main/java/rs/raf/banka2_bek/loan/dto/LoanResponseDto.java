package rs.raf.banka2_bek.loan.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Value
@Builder
public class LoanResponseDto {
    Long id;
    String loanNumber;
    String loanType;
    String interestType;
    BigDecimal amount;
    Integer repaymentPeriod;
    BigDecimal nominalRate;
    BigDecimal effectiveRate;
    BigDecimal monthlyPayment;
    LocalDate startDate;
    LocalDate endDate;
    BigDecimal remainingDebt;
    String currency;
    String status;
    String accountNumber;
    String loanPurpose;
    LocalDateTime createdAt;
}
