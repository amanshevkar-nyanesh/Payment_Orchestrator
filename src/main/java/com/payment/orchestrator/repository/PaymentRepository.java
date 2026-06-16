package com.payment.orchestrator.repository;

import com.payment.orchestrator.domain.Payment;
import com.payment.orchestrator.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentId(String paymentId);

    Optional<Payment> findByPaymentIdAndMerchantId(String paymentId, String merchantId);

    List<Payment> findByMerchantId(String merchantId);

    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.updatedAt < :threshold")
    List<Payment> findStuckPayments(PaymentStatus status, Instant threshold);

    @Query(value = "SELECT pg_advisory_xact_lock(:lockId)", nativeQuery = true)
    void acquireAdvisoryLock(long lockId);
}
