package rs.raf.banka2_bek.loan.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Value
@Builder
public class LoanRequestResponseDto {
    Long id;
    String loanType;
    String interestType;
    BigDecimal amount;
    String currency;
    String loanPurpose;
    Integer repaymentPeriod;
    String accountNumber;
    String phoneNumber;
    String employmentStatus;
    BigDecimal monthlyIncome;
    Boolean permanentEmployment;
    Integer employmentPeriod;
    String status;
    LocalDateTime createdAt;
    String clientEmail;
    String clientName;
}
