package rs.raf.banka2_bek.loan.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

@Value
@Builder
public class InstallmentResponseDto {
    Long id;
    BigDecimal amount;
    BigDecimal interestRate;
    String currency;
    LocalDate expectedDueDate;
    LocalDate actualDueDate;
    Boolean paid;
}
