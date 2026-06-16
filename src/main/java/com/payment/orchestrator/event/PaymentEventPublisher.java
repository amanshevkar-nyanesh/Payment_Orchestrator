package com.payment.orchestrator.event;

import com.payment.orchestrator.domain.Payment;
import com.payment.orchestrator.domain.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisher {

    public static final String TOPIC = "payment-events";

    private final KafkaTemplate<String, PaymentDomainEvent> kafkaTemplate;

    public void publishCreated(Payment payment) {
        publish(PaymentEventTypes.PAYMENT_CREATED, payment);
    }

    public void publishProcessing(Payment payment) {
        publish(PaymentEventTypes.PAYMENT_PROCESSING, payment);
    }

    public void publishSucceeded(Payment payment) {
        publish(PaymentEventTypes.PAYMENT_SUCCEEDED, payment);
    }

    public void publishFailed(Payment payment) {
        publish(PaymentEventTypes.PAYMENT_FAILED, payment);
    }

    private void publish(String eventType, Payment payment) {
        PaymentDomainEvent event = PaymentDomainEvent.builder()
                .eventType(eventType)
                .paymentId(payment.getPaymentId())
                .merchantId(payment.getMerchantId())
                .customerId(payment.getCustomerId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .pspName(payment.getPspName())
                .correlationId(MDC.get("correlationId"))
                .timestamp(Instant.now())
                .build();

        kafkaTemplate.send(TOPIC, payment.getPaymentId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event {} for payment {}", eventType, payment.getPaymentId(), ex);
                    } else {
                        log.info("Published event {} for payment {}", eventType, payment.getPaymentId());
                    }
                });
    }

    public void publishStatusChange(Payment payment, PaymentStatus previousStatus) {
        switch (payment.getStatus()) {
            case PROCESSING -> publishProcessing(payment);
            case SUCCESS -> publishSucceeded(payment);
            case FAILED -> publishFailed(payment);
            default -> {
                if (previousStatus == null) {
                    publishCreated(payment);
                }
            }
        }
    }
}
