package com.payment.orchestrator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "app.psp")
@Data
public class PspRoutingProperties {

    private RoutingConfig routing = new RoutingConfig();
    private Map<String, PspSimulationConfig> simulation = new HashMap<>();

    @Data
    public static class RoutingConfig {
        private Map<String, List<String>> merchants = new HashMap<>();
        private List<String> defaultPsps = List.of("PSP_A", "PSP_B", "PSP_C");
    }

    @Data
    public static class PspSimulationConfig {
        private double successRate = 0.7;
        private double timeoutRate = 0.1;
        private double temporaryFailureRate = 0.1;
        private long slowResponseMs = 2000;
    }
}
