package com.payment.orchestrator.controller;

import com.payment.orchestrator.dto.CreatePaymentRequest;
import com.payment.orchestrator.dto.PaymentDetailResponse;
import com.payment.orchestrator.dto.PaymentResponse;
import com.payment.orchestrator.security.UserPrincipal;
import com.payment.orchestrator.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal principal) {

        authorizeMerchant(request.getMerchantId(), principal);

        PaymentResponse response = paymentService.createPayment(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentDetailResponse> getPayment(
            @PathVariable String paymentId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(paymentService.getPayment(paymentId, principal));
    }

    @GetMapping
    public ResponseEntity<List<PaymentDetailResponse>> listPayments(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(paymentService.listPayments(principal));
    }

    private void authorizeMerchant(String merchantId, UserPrincipal principal) {
        if (principal.isAdmin()) {
            return;
        }
        if (!merchantId.equals(principal.getMerchantId())) {
            throw new com.payment.orchestrator.exception.UnauthorizedAccessException(
                    "Merchants can only create payments for their own merchant ID");
        }
    }
}
