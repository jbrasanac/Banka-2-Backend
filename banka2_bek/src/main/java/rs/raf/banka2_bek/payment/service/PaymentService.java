package rs.raf.banka2_bek.payment.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import rs.raf.banka2_bek.payment.dto.CreatePaymentRequestDto;
import rs.raf.banka2_bek.payment.dto.PaymentListItemDto;
import rs.raf.banka2_bek.payment.dto.PaymentResponseDto;

public interface PaymentService {

    PaymentResponseDto createPayment(CreatePaymentRequestDto request);

    Page<PaymentListItemDto> getPayments(Pageable pageable);

    PaymentResponseDto getPaymentById(Long paymentId);

    Page<PaymentListItemDto> getPaymentHistory(Pageable pageable);
}

