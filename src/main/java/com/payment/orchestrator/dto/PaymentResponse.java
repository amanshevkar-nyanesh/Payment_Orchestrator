package com.payment.orchestrator.dto;

import com.payment.orchestrator.domain.PaymentStatus;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {

    private String paymentId;
    private PaymentStatus status;
}
