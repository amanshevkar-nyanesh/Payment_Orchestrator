package com.payment.orchestrator.domain;

public enum WebhookProcessingStatus {
    RECEIVED,
    PROCESSED,
    DUPLICATE,
    IGNORED,
    FAILED
}
