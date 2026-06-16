package com.payment.orchestrator.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookResponse {

    private String paymentId;
    private String status;
    private String message;
}
