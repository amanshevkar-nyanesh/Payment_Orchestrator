package com.payment.orchestrator.repository;

import com.payment.orchestrator.domain.ReconciliationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationLogRepository extends JpaRepository<ReconciliationLog, Long> {
}
