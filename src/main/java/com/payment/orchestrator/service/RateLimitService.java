package com.payment.orchestrator.service;

import com.payment.orchestrator.config.AppProperties;
import com.payment.orchestrator.domain.MerchantRateLimit;
import com.payment.orchestrator.exception.RateLimitExceededException;
import com.payment.orchestrator.repository.MerchantRateLimitRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final MerchantRateLimitRepository repository;
    private final AppProperties appProperties;
    private final MeterRegistry meterRegistry;

    @Transactional
    public void checkAndIncrement(String merchantId) {
        int limit = appProperties.getRateLimit().getMerchantRequestsPerMinute();
        MerchantRateLimit rateLimit = repository.findByMerchantIdForUpdate(merchantId)
                .orElseGet(() -> MerchantRateLimit.builder()
                        .merchantId(merchantId)
                        .requestCount(0)
                        .windowStart(Instant.now())
                        .build());

        Instant now = Instant.now();
        if (Duration.between(rateLimit.getWindowStart(), now).toMinutes() >= 1) {
            rateLimit.setRequestCount(0);
            rateLimit.setWindowStart(now);
        }

        if (rateLimit.getRequestCount() >= limit) {
            meterRegistry.counter("rate_limit.exceeded", "merchant", merchantId).increment();
            throw new RateLimitExceededException(merchantId);
        }

        rateLimit.setRequestCount(rateLimit.getRequestCount() + 1);
        repository.save(rateLimit);
    }
}
