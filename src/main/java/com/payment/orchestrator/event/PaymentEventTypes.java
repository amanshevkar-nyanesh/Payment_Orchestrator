package com.payment.orchestrator.event;

public final class PaymentEventTypes {

    public static final String PAYMENT_CREATED = "PaymentCreated";
    public static final String PAYMENT_PROCESSING = "PaymentProcessing";
    public static final String PAYMENT_SUCCEEDED = "PaymentSucceeded";
    public static final String PAYMENT_FAILED = "PaymentFailed";

    private PaymentEventTypes() {
    }
}
