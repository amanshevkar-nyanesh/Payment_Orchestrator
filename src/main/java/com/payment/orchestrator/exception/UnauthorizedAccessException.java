package com.payment.orchestrator.exception;

public class UnauthorizedAccessException extends PaymentOrchestratorException {

    public UnauthorizedAccessException(String message) {
        super(message);
    }
}
