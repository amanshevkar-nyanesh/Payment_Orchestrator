package com.payment.orchestrator.psp;

import com.payment.orchestrator.config.PspRoutingProperties;
import com.payment.orchestrator.domain.AttemptStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
@Slf4j
public class SimulatedPspClient implements PspClient {

    private final PspRoutingProperties pspRoutingProperties;

    @Override
    public PspResult processPayment(String pspName, String paymentId, java.math.BigDecimal amount, String currency) {
        PspRoutingProperties.PspSimulationConfig config = pspRoutingProperties.getSimulation()
                .getOrDefault(pspName, new PspRoutingProperties.PspSimulationConfig());

        long start = System.currentTimeMillis();
        double roll = ThreadLocalRandom.current().nextDouble();

        if (roll < config.getTimeoutRate()) {
            simulateDelay(config.getSlowResponseMs() * 2);
            long latency = System.currentTimeMillis() - start;
            log.info("PSP {} timed out for payment {}", pspName, paymentId);
            return PspResult.builder()
                    .pspName(pspName)
                    .status(AttemptStatus.TIMEOUT)
                    .errorCode("TIMEOUT")
                    .errorMessage("PSP request timed out")
                    .latencyMs(latency)
                    .build();
        }

        if (roll < config.getTimeoutRate() + config.getTemporaryFailureRate()) {
            simulateDelay(500);
            long latency = System.currentTimeMillis() - start;
            log.info("PSP {} temporary failure for payment {}", pspName, paymentId);
            return PspResult.builder()
                    .pspName(pspName)
                    .status(AttemptStatus.TEMPORARY_FAILURE)
                    .errorCode("TEMP_FAILURE")
                    .errorMessage("Temporary PSP failure")
                    .latencyMs(latency)
                    .build();
        }

        double failureBudget = Math.max(0, 1.0 - config.getSuccessRate() - config.getTimeoutRate() - config.getTemporaryFailureRate());
        if (roll < config.getTimeoutRate() + config.getTemporaryFailureRate() + failureBudget) {
            simulateDelay(300);
            long latency = System.currentTimeMillis() - start;
            return PspResult.builder()
                    .pspName(pspName)
                    .status(AttemptStatus.PERMANENT_FAILURE)
                    .errorCode("DECLINED")
                    .errorMessage("Payment declined by PSP")
                    .latencyMs(latency)
                    .build();
        }

        simulateDelay(config.getSlowResponseMs());
        long latency = System.currentTimeMillis() - start;
        String reference = pspName + "-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("PSP {} succeeded for payment {} with ref {}", pspName, paymentId, reference);
        return PspResult.builder()
                .pspName(pspName)
                .status(AttemptStatus.SUCCESS)
                .pspReference(reference)
                .latencyMs(latency)
                .build();
    }

    @Override
    public String queryPaymentStatus(String pspName, String paymentId) {
        PspRoutingProperties.PspSimulationConfig config = pspRoutingProperties.getSimulation()
                .getOrDefault(pspName, new PspRoutingProperties.PspSimulationConfig());
        double roll = ThreadLocalRandom.current().nextDouble();
        if (roll < config.getSuccessRate()) {
            return "SUCCESS";
        }
        if (roll < config.getSuccessRate() + 0.2) {
            return "PROCESSING";
        }
        return "FAILED";
    }

    private void simulateDelay(long ms) {
        try {
            Thread.sleep(Math.min(ms, 5000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
