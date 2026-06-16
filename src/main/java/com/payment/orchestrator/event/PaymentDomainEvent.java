package com.payment.orchestrator.event;

import com.payment.orchestrator.domain.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentDomainEvent {

    private String eventType;
    private String paymentId;
    private String merchantId;
    private String customerId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String pspName;
    private String correlationId;
    private Instant timestamp;
}
