package com.payment.orchestrator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.orchestrator.dto.WebhookPayload;
import com.payment.orchestrator.dto.WebhookResponse;
import com.payment.orchestrator.service.WebhookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;

    @PostMapping("/{psp}")
    public ResponseEntity<WebhookResponse> receiveWebhook(
            @PathVariable String psp,
            @Valid @RequestBody WebhookPayload payload) throws Exception {

        String rawPayload = objectMapper.writeValueAsString(payload);
        WebhookResponse response = webhookService.processWebhook(psp, payload, rawPayload);
        return ResponseEntity.ok(response);
    }
}
