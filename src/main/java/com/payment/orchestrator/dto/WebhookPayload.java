package com.payment.orchestrator.dto;

import com.payment.orchestrator.domain.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookPayload {

    @NotBlank
    private String paymentId;

    @NotBlank
    private String status;
}
