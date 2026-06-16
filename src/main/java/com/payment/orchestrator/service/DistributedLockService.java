package com.payment.orchestrator.service;

import com.payment.orchestrator.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedLockService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public <T> T executeWithLock(String lockKey, Supplier<T> action) {
        paymentRepository.acquireAdvisoryLock(lockKey.hashCode());
        return action.get();
    }

    @Transactional
    public void executeWithLock(String lockKey, Runnable action) {
        paymentRepository.acquireAdvisoryLock(lockKey.hashCode());
        action.run();
    }
}
