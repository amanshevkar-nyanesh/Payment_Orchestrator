package com.payment.orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.orchestrator.domain.*;
import com.payment.orchestrator.dto.WebhookPayload;
import com.payment.orchestrator.dto.WebhookResponse;
import com.payment.orchestrator.event.PaymentEventPublisher;
import com.payment.orchestrator.exception.PaymentOrchestratorException;
import com.payment.orchestrator.repository.PaymentRepository;
import com.payment.orchestrator.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final WebhookEventRepository webhookEventRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentStateService paymentStateService;
    private final PaymentEventPublisher eventPublisher;
    private final DistributedLockService distributedLockService;
    private final ObjectMapper objectMapper;

    @Transactional
    public WebhookResponse processWebhook(String pspName, WebhookPayload payload, String rawPayload) {
        String payloadHash = hashPayload(rawPayload);

        try {
            WebhookEvent event = WebhookEvent.builder()
                    .pspName(pspName.toUpperCase())
                    .paymentId(payload.getPaymentId())
                    .eventStatus(payload.getStatus().toUpperCase())
                    .payloadHash(payloadHash)
                    .rawPayload(rawPayload)
                    .processingStatus(WebhookProcessingStatus.RECEIVED)
                    .build();
            webhookEventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate webhook received for payment {} from {}", payload.getPaymentId(), pspName);
            return WebhookResponse.builder()
                    .paymentId(payload.getPaymentId())
                    .status(payload.getStatus())
                    .message("Duplicate webhook - already processed")
                    .build();
        }

        return distributedLockService.executeWithLock(
                "webhook:" + payload.getPaymentId(),
                () -> applyWebhook(pspName, payload, payloadHash, rawPayload));
    }

    private WebhookResponse applyWebhook(String pspName, WebhookPayload payload, String payloadHash, String rawPayload) {
        Payment payment = paymentRepository.findByPaymentId(payload.getPaymentId())
                .orElseThrow(() -> new PaymentOrchestratorException("Payment not found: " + payload.getPaymentId()));

        PaymentStatus webhookStatus = mapWebhookStatus(payload.getStatus());
        WebhookEvent event = webhookEventRepository
                .findByPspNameAndPaymentIdAndPayloadHash(pspName.toUpperCase(), payload.getPaymentId(), payloadHash)
                .orElseThrow();

        if (payment.getStatus() == PaymentStatus.SUCCESS && webhookStatus == PaymentStatus.PROCESSING) {
            event.setProcessingStatus(WebhookProcessingStatus.IGNORED);
            event.setProcessedAt(Instant.now());
            webhookEventRepository.save(event);
            return WebhookResponse.builder()
                    .paymentId(payload.getPaymentId())
                    .status(payment.getStatus().name())
                    .message("Out-of-order webhook ignored")
                    .build();
        }

        if (payment.getStatus() == webhookStatus) {
            event.setProcessingStatus(WebhookProcessingStatus.DUPLICATE);
            event.setProcessedAt(Instant.now());
            webhookEventRepository.save(event);
            return WebhookResponse.builder()
                    .paymentId(payload.getPaymentId())
                    .status(payment.getStatus().name())
                    .message("Status unchanged")
                    .build();
        }

        PaymentStatus previous = payment.getStatus();
        payment = paymentStateService.transitionIfAllowed(
                payment, webhookStatus, "Webhook from " + pspName, "webhook");

        if (webhookStatus == PaymentStatus.SUCCESS) {
            payment.setPspName(pspName.toUpperCase());
            paymentRepository.save(payment);
            eventPublisher.publishSucceeded(payment);
        } else if (webhookStatus == PaymentStatus.FAILED) {
            eventPublisher.publishFailed(payment);
        }

        event.setProcessingStatus(WebhookProcessingStatus.PROCESSED);
        event.setProcessedAt(Instant.now());
        webhookEventRepository.save(event);

        log.info("Webhook processed for payment {} status {} -> {}", payment.getPaymentId(), previous, payment.getStatus());

        return WebhookResponse.builder()
                .paymentId(payment.getPaymentId())
                .status(payment.getStatus().name())
                .message("Webhook processed successfully")
                .build();
    }

    private PaymentStatus mapWebhookStatus(String status) {
        return switch (status.toUpperCase()) {
            case "SUCCESS" -> PaymentStatus.SUCCESS;
            case "FAILED" -> PaymentStatus.FAILED;
            case "PROCESSING" -> PaymentStatus.PROCESSING;
            case "PENDING" -> PaymentStatus.PENDING;
            default -> throw new PaymentOrchestratorException("Unknown webhook status: " + status);
        };
    }

    private String hashPayload(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
