package com.payment.orchestrator.dto;

import com.payment.orchestrator.domain.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentDetailResponse {

    private String paymentId;
    private String merchantId;
    private String customerId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String pspName;
    private Instant createdAt;
    private Instant updatedAt;
}
