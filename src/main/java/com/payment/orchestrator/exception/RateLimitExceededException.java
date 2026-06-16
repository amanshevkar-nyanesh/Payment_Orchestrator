package com.payment.orchestrator.exception;

public class RateLimitExceededException extends PaymentOrchestratorException {

    public RateLimitExceededException(String merchantId) {
        super("Rate limit exceeded for merchant: " + merchantId);
    }
}
