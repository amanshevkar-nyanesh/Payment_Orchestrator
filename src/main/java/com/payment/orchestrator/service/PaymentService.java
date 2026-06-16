package com.payment.orchestrator.service;

import com.payment.orchestrator.domain.Payment;
import com.payment.orchestrator.domain.PaymentStatus;
import com.payment.orchestrator.dto.CreatePaymentRequest;
import com.payment.orchestrator.dto.PaymentDetailResponse;
import com.payment.orchestrator.dto.PaymentResponse;
import com.payment.orchestrator.event.PaymentEventPublisher;
import com.payment.orchestrator.exception.PaymentOrchestratorException;
import com.payment.orchestrator.exception.UnauthorizedAccessException;
import com.payment.orchestrator.repository.PaymentRepository;
import com.payment.orchestrator.security.UserPrincipal;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentStateService paymentStateService;
    private final PaymentRoutingService paymentRoutingService;
    private final IdempotencyService idempotencyService;
    private final RateLimitService rateLimitService;
    private final PaymentEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request, String idempotencyKey) {
        rateLimitService.checkAndIncrement(request.getMerchantId());

        return idempotencyService.executeWithIdempotency(
                idempotencyKey,
                request.getMerchantId(),
                () -> doCreatePayment(request));
    }

    private PaymentResponse doCreatePayment(CreatePaymentRequest request) {
        String paymentId = generatePaymentId();

        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .merchantId(request.getMerchantId())
                .customerId(request.getCustomerId())
                .amount(request.getAmount())
                .currency(request.getCurrency().toUpperCase())
                .status(PaymentStatus.CREATED)
                .build();

        payment = paymentRepository.save(payment);
        paymentStateService.transition(payment, PaymentStatus.PENDING, "Payment created", "system");
        eventPublisher.publishCreated(payment);
        meterRegistry.counter("payment.created", "merchant", request.getMerchantId()).increment();

        log.info("Created payment {} for merchant {}", paymentId, request.getMerchantId());

        paymentRoutingService.routeAndProcessAsync(payment);

        return PaymentResponse.builder()
                .paymentId(paymentId)
                .status(PaymentStatus.PENDING)
                .build();
    }

    public PaymentDetailResponse getPayment(String paymentId, UserPrincipal principal) {
        Payment payment = paymentRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new PaymentOrchestratorException("Payment not found: " + paymentId));

        authorizeAccess(payment, principal);

        return toDetailResponse(payment);
    }

    public List<PaymentDetailResponse> listPayments(UserPrincipal principal) {
        List<Payment> payments;
        if (principal.isAdmin()) {
            payments = paymentRepository.findAll();
        } else {
            payments = paymentRepository.findByMerchantId(principal.getMerchantId());
        }
        return payments.stream().map(this::toDetailResponse).toList();
    }

    private void authorizeAccess(Payment payment, UserPrincipal principal) {
        if (principal.isAdmin()) {
            return;
        }
        if (!payment.getMerchantId().equals(principal.getMerchantId())) {
            throw new UnauthorizedAccessException("Access denied to payment: " + payment.getPaymentId());
        }
    }

    private PaymentDetailResponse toDetailResponse(Payment payment) {
        return PaymentDetailResponse.builder()
                .paymentId(payment.getPaymentId())
                .merchantId(payment.getMerchantId())
                .customerId(payment.getCustomerId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .pspName(payment.getPspName())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }

    private String generatePaymentId() {

        return "PAY-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();

    }
}
