package rs.raf.banka2_bek.payment.service.implementation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.payment.dto.CreatePaymentRequestDto;
import rs.raf.banka2_bek.payment.dto.PaymentResponseDto;
import rs.raf.banka2_bek.payment.model.Payment;
import rs.raf.banka2_bek.payment.repository.PaymentRepository;
import rs.raf.banka2_bek.payment.service.PaymentService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;

    @Override
    public PaymentResponseDto createPayment(CreatePaymentRequestDto request) {
        throw new UnsupportedOperationException("Payment creation is not implemented yet.");
    }

    @Override
    public List<PaymentResponseDto> getPayments() {
        return paymentRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public PaymentResponseDto getPaymentById(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment with ID " + paymentId + " not found."));
        return toResponse(payment);
    }

    @Override
    public List<PaymentResponseDto> getPaymentHistory() {
        // For now history returns the same list as /payments.
        return getPayments();
    }

    private PaymentResponseDto toResponse(Payment payment) {
        return PaymentResponseDto.builder()
                .id(payment.getId())
                .orderNumber(payment.getOrderNumber())
                .fromAccount(payment.getFromAccount() != null ? payment.getFromAccount().getAccountNumber() : null)
                .toAccount(payment.getToAccountNumber())
                .amount(payment.getAmount())
                .paymentCode(payment.getPaymentCode())
                .referenceNumber(payment.getReferenceNumber())
                .description(payment.getPurpose())
                .status(payment.getStatus())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}

