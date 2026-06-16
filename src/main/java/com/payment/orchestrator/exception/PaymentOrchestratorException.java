package com.payment.orchestrator.exception;

public class PaymentOrchestratorException extends RuntimeException {

    public PaymentOrchestratorException(String message) {
        super(message);
    }

    public PaymentOrchestratorException(String message, Throwable cause) {
        super(message, cause);
    }
}
