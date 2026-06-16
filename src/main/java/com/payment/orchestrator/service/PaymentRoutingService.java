package com.payment.orchestrator.service;

import com.payment.orchestrator.domain.*;
import com.payment.orchestrator.event.PaymentEventPublisher;
import com.payment.orchestrator.psp.PspClient;
import com.payment.orchestrator.psp.PspResult;
import com.payment.orchestrator.repository.PaymentAttemptRepository;
import com.payment.orchestrator.repository.PaymentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentRoutingService {

    private final PspRoutingService pspRoutingService;
    private final PspClient pspClient;
    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final PaymentStateService paymentStateService;
    private final CircuitBreakerService circuitBreakerService;
    private final PaymentEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;
    private final DistributedLockService distributedLockService;

    @Async("paymentExecutor")
    public void routeAndProcessAsync(Payment payment) {
        routeAndProcess(payment);
    }

    @Transactional
    public Payment routeAndProcess(Payment payment) {
        return distributedLockService.executeWithLock("payment-routing:" + payment.getPaymentId(), () -> {
            Payment current = paymentRepository.findByPaymentId(payment.getPaymentId()).orElseThrow();
            if (current.getStatus() == PaymentStatus.SUCCESS || current.getStatus() == PaymentStatus.FAILED) {
                return current;
            }

            paymentStateService.transition(current, PaymentStatus.PENDING, "Payment queued for routing", "system");
            List<String> psps = pspRoutingService.getPspOrderForMerchant(current.getMerchantId());

            if (psps.isEmpty()) {
                paymentStateService.transition(current, PaymentStatus.FAILED, "No available PSPs", "system");
                eventPublisher.publishFailed(current);
                return current;
            }

            int attemptNumber = 1;
            for (String pspName : psps) {
                paymentStateService.transition(current, PaymentStatus.PROCESSING,
                        "Routing to " + pspName, "system");
                eventPublisher.publishProcessing(current);

                Timer.Sample sample = Timer.start(meterRegistry);
                PspResult result = pspClient.processPayment(
                        pspName, current.getPaymentId(), current.getAmount(), current.getCurrency());
                sample.stop(meterRegistry.timer("psp.latency", "psp", pspName));

                recordAttempt(current.getPaymentId(), pspName, attemptNumber++, result);

                if (result.getStatus() == AttemptStatus.SUCCESS) {
                    current.setPspName(pspName);
                    current.setPspReference(result.getPspReference());
                    paymentRepository.save(current);
                    circuitBreakerService.recordSuccess(pspName);
                    paymentStateService.transition(current, PaymentStatus.SUCCESS,
                            "PSP " + pspName + " succeeded", "system");
                    meterRegistry.counter("payment.success", "psp", pspName).increment();
                    eventPublisher.publishSucceeded(current);
                    return current;
                }

                circuitBreakerService.recordFailure(pspName);
                meterRegistry.counter("psp.failure", "psp", pspName, "reason", result.getStatus().name()).increment();
                log.warn("PSP {} failed for payment {} with status {}", pspName, current.getPaymentId(), result.getStatus());
            }

            paymentStateService.transition(current, PaymentStatus.FAILED, "All PSPs exhausted", "system");
            meterRegistry.counter("payment.failed").increment();
            eventPublisher.publishFailed(current);
            return current;
        });
    }

    private void recordAttempt(String paymentId, String pspName, int attemptNumber, PspResult result) {
        attemptRepository.save(PaymentAttempt.builder()
                .paymentId(paymentId)
                .pspName(pspName)
                .attemptNumber(attemptNumber)
                .status(result.getStatus())
                .errorCode(result.getErrorCode())
                .errorMessage(result.getErrorMessage())
                .latencyMs(result.getLatencyMs())
                .build());
    }
}
