package com.payment.orchestrator.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "circuit_breaker_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CircuitBreakerState {

    @Id
    @Column(name = "psp_name")
    private String pspName;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(nullable = false)
    private String state;

    @Column(name = "opened_at")
    private Instant openedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
