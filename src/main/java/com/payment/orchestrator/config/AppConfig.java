package com.payment.orchestrator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
public class AppConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    public KafkaTemplate<String, com.payment.orchestrator.event.PaymentDomainEvent> kafkaTemplate(
            ProducerFactory<String, com.payment.orchestrator.event.PaymentDomainEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
