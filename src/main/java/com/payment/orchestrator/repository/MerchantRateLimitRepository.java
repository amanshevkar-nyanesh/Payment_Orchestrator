package com.payment.orchestrator.repository;

import com.payment.orchestrator.domain.MerchantRateLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface MerchantRateLimitRepository extends JpaRepository<MerchantRateLimit, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MerchantRateLimit m WHERE m.merchantId = :merchantId")
    Optional<MerchantRateLimit> findByMerchantIdForUpdate(String merchantId);
}
