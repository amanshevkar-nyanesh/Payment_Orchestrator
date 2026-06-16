package com.payment.orchestrator.service;

import com.payment.orchestrator.config.AppProperties;
import com.payment.orchestrator.domain.CircuitBreakerState;
import com.payment.orchestrator.repository.CircuitBreakerStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class CircuitBreakerService {

    private final CircuitBreakerStateRepository repository;
    private final AppProperties appProperties;

    public boolean isPspAvailable(String pspName) {
        return repository.findById(pspName)
                .map(this::evaluateState)
                .orElse(true);
    }

    private boolean evaluateState(CircuitBreakerState state) {
        if ("CLOSED".equals(state.getState())) {
            return true;
        }
        if ("OPEN".equals(state.getState()) && state.getOpenedAt() != null) {
            long elapsed = Duration.between(state.getOpenedAt(), Instant.now()).getSeconds();
            if (elapsed >= appProperties.getCircuitBreaker().getCooldownSeconds()) {
                return true;
            }
        }
        return false;
    }

    @Transactional
    public void recordSuccess(String pspName) {
        CircuitBreakerState state = getOrCreate(pspName);
        state.setFailureCount(0);
        state.setState("CLOSED");
        state.setOpenedAt(null);
        repository.save(state);
    }

    @Transactional
    public void recordFailure(String pspName) {
        CircuitBreakerState state = repository.findByPspNameForUpdate(pspName)
                .orElseGet(() -> getOrCreate(pspName));
        state.setFailureCount(state.getFailureCount() + 1);
        if (state.getFailureCount() >= appProperties.getCircuitBreaker().getFailureThreshold()) {
            state.setState("OPEN");
            state.setOpenedAt(Instant.now());
            log.warn("Circuit breaker OPEN for PSP {}", pspName);
        }
        repository.save(state);
    }

    @Transactional
    public void tryHalfOpen(String pspName) {
        repository.findByPspNameForUpdate(pspName).ifPresent(state -> {
            if ("OPEN".equals(state.getState())) {
                state.setState("HALF_OPEN");
                repository.save(state);
            }
        });
    }

    private CircuitBreakerState getOrCreate(String pspName) {
        return repository.findById(pspName).orElseGet(() ->
                repository.save(CircuitBreakerState.builder()
                        .pspName(pspName)
                        .failureCount(0)
                        .state("CLOSED")
                        .build()));
    }
}
