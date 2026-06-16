package com.payment.orchestrator.domain;

public enum PaymentStatus {
    CREATED,
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED,
    REFUNDED
}
