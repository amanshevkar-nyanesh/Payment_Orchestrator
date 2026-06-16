package com.payment.orchestrator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
@Data
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Reconciliation reconciliation = new Reconciliation();
    private RateLimit rateLimit = new RateLimit();
    private CircuitBreaker circuitBreaker = new CircuitBreaker();

    @Data
    public static class Jwt {
        private String secret;
        private long expirationMs;
    }

    @Data
    public static class Reconciliation {
        private String cron;
        private int stuckThresholdMinutes;
    }

    @Data
    public static class RateLimit {
        private int merchantRequestsPerMinute;
    }

    @Data
    public static class CircuitBreaker {
        private int failureThreshold;
        private int cooldownSeconds;
    }
}
