package rs.raf.banka2_bek.payment.service;

import rs.raf.banka2_bek.payment.dto.CreatePaymentRequestDto;
import rs.raf.banka2_bek.payment.dto.PaymentResponseDto;

import java.util.List;

public interface PaymentService {

    PaymentResponseDto createPayment(CreatePaymentRequestDto request);

    List<PaymentResponseDto> getPayments();

    PaymentResponseDto getPaymentById(Long paymentId);

    List<PaymentResponseDto> getPaymentHistory();
}

