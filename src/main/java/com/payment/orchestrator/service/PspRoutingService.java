package com.payment.orchestrator.service;

import com.payment.orchestrator.config.PspRoutingProperties;
import com.payment.orchestrator.domain.CircuitBreakerState;
import com.payment.orchestrator.repository.CircuitBreakerStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PspRoutingService {

    private final PspRoutingProperties pspRoutingProperties;
    private final CircuitBreakerService circuitBreakerService;
    private final CircuitBreakerStateRepository circuitBreakerStateRepository;

    public List<String> getPspOrderForMerchant(String merchantId) {
        List<String> configured = pspRoutingProperties.getRouting().getMerchants()
                .getOrDefault(merchantId, pspRoutingProperties.getRouting().getDefaultPsps());

        return configured.stream()
                .filter(circuitBreakerService::isPspAvailable)
                .collect(Collectors.toList());
    }

    @Transactional
    public void initializeCircuitBreakers() {
        List<String> allPsps = pspRoutingProperties.getRouting().getDefaultPsps();
        for (String psp : allPsps) {
            if (circuitBreakerStateRepository.findById(psp).isEmpty()) {
                circuitBreakerStateRepository.save(CircuitBreakerState.builder()
                        .pspName(psp)
                        .failureCount(0)
                        .state("CLOSED")
                        .openedAt(null)
                        .build());
            }
        }
    }
}
