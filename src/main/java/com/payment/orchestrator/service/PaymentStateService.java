package com.payment.orchestrator.service;

import com.payment.orchestrator.domain.Payment;
import com.payment.orchestrator.domain.PaymentStateAudit;
import com.payment.orchestrator.domain.PaymentStatus;
import com.payment.orchestrator.repository.PaymentRepository;
import com.payment.orchestrator.repository.PaymentStateAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentStateService {

    private final PaymentRepository paymentRepository;
    private final PaymentStateAuditRepository auditRepository;
    private final PaymentStateMachine stateMachine;

    @Transactional
    public Payment transition(Payment payment, PaymentStatus newStatus, String reason, String actor) {
        PaymentStatus current = payment.getStatus();
        stateMachine.validateTransition(current, newStatus);
        payment.setStatus(newStatus);
        Payment saved = paymentRepository.save(payment);

        auditRepository.save(PaymentStateAudit.builder()
                .paymentId(payment.getPaymentId())
                .fromStatus(current.name())
                .toStatus(newStatus.name())
                .reason(reason)
                .actor(actor)
                .build());

        return saved;
    }

    @Transactional
    public Payment transitionIfAllowed(Payment payment, PaymentStatus newStatus, String reason, String actor) {
        if (!stateMachine.canTransition(payment.getStatus(), newStatus)) {
            return payment;
        }
        if (stateMachine.getStatusPriority(newStatus) <= stateMachine.getStatusPriority(payment.getStatus())
                && newStatus != payment.getStatus()) {
            return payment;
        }
        return transition(payment, newStatus, reason, actor);
    }
}
