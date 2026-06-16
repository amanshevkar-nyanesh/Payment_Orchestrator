package com.payment.orchestrator.repository;

import com.payment.orchestrator.domain.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByIdempotencyKeyAndMerchantId(String idempotencyKey, String merchantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ik FROM IdempotencyKey ik WHERE ik.idempotencyKey = :key AND ik.merchantId = :merchantId")
    Optional<IdempotencyKey> findByKeyAndMerchantForUpdate(String key, String merchantId);
}
