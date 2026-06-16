package com.payment.orchestrator.job;

import com.payment.orchestrator.config.AppProperties;
import com.payment.orchestrator.domain.Payment;
import com.payment.orchestrator.domain.PaymentStatus;
import com.payment.orchestrator.domain.ReconciliationLog;
import com.payment.orchestrator.event.PaymentEventPublisher;
import com.payment.orchestrator.psp.PspClient;
import com.payment.orchestrator.repository.PaymentRepository;
import com.payment.orchestrator.repository.ReconciliationLogRepository;
import com.payment.orchestrator.service.DistributedLockService;
import com.payment.orchestrator.service.PaymentStateService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReconciliationJob {

    private final PaymentRepository paymentRepository;
    private final ReconciliationLogRepository reconciliationLogRepository;
    private final PspClient pspClient;
    private final PaymentStateService paymentStateService;
    private final PaymentEventPublisher eventPublisher;
    private final AppProperties appProperties;
    private final DistributedLockService distributedLockService;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "${app.reconciliation.cron:0 0 * * * *}")
    public void reconcileStuckPayments() {
        distributedLockService.executeWithLock("reconciliation-job", () -> {
            Instant threshold = Instant.now().minus(
                    appProperties.getReconciliation().getStuckThresholdMinutes(), ChronoUnit.MINUTES);

            List<Payment> stuckPayments = paymentRepository.findStuckPayments(PaymentStatus.PROCESSING, threshold);
            log.info("Reconciliation job found {} stuck payments", stuckPayments.size());

            for (Payment payment : stuckPayments) {
                reconcilePayment(payment);
            }

            meterRegistry.counter("reconciliation.runs").increment();
            meterRegistry.counter("reconciliation.payments_processed").increment(stuckPayments.size());
        });
    }

    private void reconcilePayment(Payment payment) {
        String pspName = payment.getPspName();
        if (pspName == null) {
            log.warn("Payment {} stuck in PROCESSING without PSP", payment.getPaymentId());
            return;
        }

        String pspStatus = pspClient.queryPaymentStatus(pspName, payment.getPaymentId());
        PaymentStatus previous = payment.getStatus();
        PaymentStatus newStatus = mapPspStatus(pspStatus);

        if (newStatus != previous) {
            payment = paymentStateService.transitionIfAllowed(
                    payment, newStatus, "Reconciliation update from PSP", "reconciliation");

            if (newStatus == PaymentStatus.SUCCESS) {
                eventPublisher.publishSucceeded(payment);
            } else if (newStatus == PaymentStatus.FAILED) {
                eventPublisher.publishFailed(payment);
            }
        }

        reconciliationLogRepository.save(ReconciliationLog.builder()
                .paymentId(payment.getPaymentId())
                .previousStatus(previous.name())
                .newStatus(payment.getStatus().name())
                .pspName(pspName)
                .pspStatus(pspStatus)
                .action("RECONCILE")
                .details("Reconciled stuck payment in PROCESSING state")
                .build());

        log.info("Reconciled payment {} from {} to {} based on PSP status {}",
                payment.getPaymentId(), previous, payment.getStatus(), pspStatus);
    }

    private PaymentStatus mapPspStatus(String pspStatus) {
        return switch (pspStatus.toUpperCase()) {
            case "SUCCESS" -> PaymentStatus.SUCCESS;
            case "FAILED" -> PaymentStatus.FAILED;
            case "PROCESSING" -> PaymentStatus.PROCESSING;
            default -> PaymentStatus.PENDING;
        };
    }
}
