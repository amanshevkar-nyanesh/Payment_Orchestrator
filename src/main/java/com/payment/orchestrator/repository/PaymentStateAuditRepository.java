package com.payment.orchestrator.repository;

import com.payment.orchestrator.domain.PaymentStateAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentStateAuditRepository extends JpaRepository<PaymentStateAudit, Long> {

    List<PaymentStateAudit> findByPaymentIdOrderByCreatedAtAsc(String paymentId);
}
