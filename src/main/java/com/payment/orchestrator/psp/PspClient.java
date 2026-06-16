package com.payment.orchestrator.psp;

import java.math.BigDecimal;

public interface PspClient {

    PspResult processPayment(String pspName, String paymentId, BigDecimal amount, String currency);

    String queryPaymentStatus(String pspName, String paymentId);
}
