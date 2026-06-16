package com.payment.orchestrator.exception;

public class InvalidStateTransitionException extends PaymentOrchestratorException {

    public InvalidStateTransitionException(String from, String to) {
        super("Invalid state transition from " + from + " to " + to);
    }
}
