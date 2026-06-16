package com.payment.orchestrator.repository;

import com.payment.orchestrator.domain.CircuitBreakerState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface CircuitBreakerStateRepository extends JpaRepository<CircuitBreakerState, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cb FROM CircuitBreakerState cb WHERE cb.pspName = :pspName")
    Optional<CircuitBreakerState> findByPspNameForUpdate(String pspName);
}
