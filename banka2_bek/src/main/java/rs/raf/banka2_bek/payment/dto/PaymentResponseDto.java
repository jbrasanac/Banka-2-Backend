package rs.raf.banka2_bek.payment.dto;

import lombok.Builder;
import lombok.Value;
import rs.raf.banka2_bek.payment.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Value
@Builder
public class PaymentResponseDto {
    Long id;
    String orderNumber;
    String fromAccount;
    String toAccount;
    BigDecimal amount;
    BigDecimal fee;
    String paymentCode;
    String referenceNumber;
    String description;
    PaymentStatus status;
    LocalDateTime createdAt;
}

