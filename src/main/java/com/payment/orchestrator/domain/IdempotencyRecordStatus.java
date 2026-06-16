package com.payment.orchestrator.domain;

public enum IdempotencyRecordStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
