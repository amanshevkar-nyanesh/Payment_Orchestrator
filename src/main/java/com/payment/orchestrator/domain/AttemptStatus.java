package com.payment.orchestrator.domain;

public enum AttemptStatus {
    SUCCESS,
    TIMEOUT,
    TEMPORARY_FAILURE,
    PERMANENT_FAILURE,
    SKIPPED
}
