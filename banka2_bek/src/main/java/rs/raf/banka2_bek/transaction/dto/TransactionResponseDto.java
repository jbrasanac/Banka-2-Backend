package rs.raf.banka2_bek.transaction.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Value
@Builder
public class TransactionResponseDto {
    Long id;
    String accountNumber;
    String currencyCode;
    String description;
    TransactionType type;
    BigDecimal debit;
    BigDecimal credit;
    BigDecimal reserved;
    BigDecimal reservedUsed;
    BigDecimal balanceAfter;
    BigDecimal availableAfter;
    LocalDateTime createdAt;
}
