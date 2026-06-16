package com.payment.orchestrator.integration;

import com.payment.orchestrator.dto.CreatePaymentRequest;
import com.payment.orchestrator.dto.LoginRequest;
import com.payment.orchestrator.dto.PaymentResponse;
import com.payment.orchestrator.repository.IdempotencyKeyRepository;
import com.payment.orchestrator.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class IdempotencyIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_orchestrator")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    private String authToken;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();

        LoginRequest login = LoginRequest.builder()
                .username("merchant_m123")
                .password("password")
                .build();
        ResponseEntity<com.payment.orchestrator.dto.LoginResponse> loginResponse =
                restTemplate.postForEntity("/auth/login", login, com.payment.orchestrator.dto.LoginResponse.class);
        authToken = loginResponse.getBody().getToken();
    }

    @Test
    void concurrentRequestsWithSameIdempotencyKeyCreateSinglePayment() throws Exception {
        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .merchantId("M123")
                .amount(new BigDecimal("100"))
                .currency("EUR")
                .customerId("C456")
                .build();

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<PaymentResponse> responses = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    HttpHeaders headers = new HttpHeaders();
                    headers.setBearerAuth(authToken);
                    headers.set("Idempotency-Key", "abc123");
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    HttpEntity<CreatePaymentRequest> entity = new HttpEntity<>(request, headers);
                    ResponseEntity<PaymentResponse> response = restTemplate.exchange(
                            "/payments", HttpMethod.POST, entity, PaymentResponse.class);
                    responses.add(response.getBody());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(idempotencyKeyRepository.count()).isEqualTo(1);
        assertThat(responses).hasSize(threadCount);
        assertThat(responses).allMatch(r -> r.getPaymentId().equals(responses.get(0).getPaymentId()));
    }
}
