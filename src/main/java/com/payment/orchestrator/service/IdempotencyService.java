package com.payment.orchestrator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.orchestrator.domain.IdempotencyKey;
import com.payment.orchestrator.domain.IdempotencyRecordStatus;
import com.payment.orchestrator.dto.PaymentResponse;
import com.payment.orchestrator.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public PaymentResponse executeWithIdempotency(
            String idempotencyKey,
            String merchantId,
            Supplier<PaymentResponse> paymentCreator) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return paymentCreator.get();
        }

        Optional<IdempotencyKey> existing = idempotencyKeyRepository
                .findByIdempotencyKeyAndMerchantId(idempotencyKey, merchantId);

        if (existing.isPresent()) {
            return handleExistingRecord(existing.get());
        }

        try {
            IdempotencyKey record = IdempotencyKey.builder()
                    .idempotencyKey(idempotencyKey)
                    .merchantId(merchantId)
                    .status(IdempotencyRecordStatus.IN_PROGRESS)
                    .build();
            idempotencyKeyRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException e) {
            log.debug("Concurrent idempotency key insert detected for key={}", idempotencyKey);
            return waitForCompletion(idempotencyKey, merchantId);
        }

        try {
            PaymentResponse response = paymentCreator.get();
            completeRecord(idempotencyKey, merchantId, response);
            return response;
        } catch (Exception e) {
            markFailed(idempotencyKey, merchantId);
            throw e;
        }
    }

    private PaymentResponse handleExistingRecord(IdempotencyKey record) {
        return switch (record.getStatus()) {
            case COMPLETED -> deserializeResponse(record.getResponseBody());
            case IN_PROGRESS -> waitForCompletion(record.getIdempotencyKey(), record.getMerchantId());
            case FAILED -> throw new IllegalStateException("Previous idempotent request failed");
        };
    }

    private PaymentResponse waitForCompletion(String key, String merchantId) {
        for (int i = 0; i < 50; i++) {
            Optional<IdempotencyKey> record = idempotencyKeyRepository
                    .findByIdempotencyKeyAndMerchantId(key, merchantId);
            if (record.isPresent()) {
                IdempotencyKey r = record.get();
                if (r.getStatus() == IdempotencyRecordStatus.COMPLETED) {
                    return deserializeResponse(r.getResponseBody());
                }
                if (r.getStatus() == IdempotencyRecordStatus.FAILED) {
                    throw new IllegalStateException("Previous idempotent request failed");
                }
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for idempotent request");
            }
        }
        throw new IllegalStateException("Timeout waiting for idempotent request completion");
    }

    private void completeRecord(String key, String merchantId, PaymentResponse response) {
        IdempotencyKey record = idempotencyKeyRepository
                .findByKeyAndMerchantForUpdate(key, merchantId)
                .orElseThrow();
        record.setPaymentId(response.getPaymentId());
        record.setResponseBody(serializeResponse(response));
        record.setStatus(IdempotencyRecordStatus.COMPLETED);
        idempotencyKeyRepository.save(record);
    }

    private void markFailed(String key, String merchantId) {
        idempotencyKeyRepository.findByKeyAndMerchantForUpdate(key, merchantId)
                .ifPresent(record -> {
                    record.setStatus(IdempotencyRecordStatus.FAILED);
                    idempotencyKeyRepository.save(record);
                });
    }

    private String serializeResponse(PaymentResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize idempotency response", e);
        }
    }

    private PaymentResponse deserializeResponse(String json) {
        try {
            return objectMapper.readValue(json, PaymentResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize idempotency response", e);
        }
    }
}
