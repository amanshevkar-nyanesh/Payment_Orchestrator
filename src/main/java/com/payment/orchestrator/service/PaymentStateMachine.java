package com.payment.orchestrator.service;

import com.payment.orchestrator.domain.PaymentStatus;
import com.payment.orchestrator.exception.InvalidStateTransitionException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class PaymentStateMachine {

    private static final Map<PaymentStatus, Set<PaymentStatus>> TRANSITIONS = new EnumMap<>(PaymentStatus.class);

    static {

        TRANSITIONS.put(PaymentStatus.CREATED, EnumSet.of(PaymentStatus.PENDING, PaymentStatus.FAILED));

        TRANSITIONS.put(PaymentStatus.PENDING, EnumSet.of(PaymentStatus.PROCESSING, PaymentStatus.FAILED));

        TRANSITIONS.put(PaymentStatus.PROCESSING, EnumSet.of(PaymentStatus.SUCCESS, PaymentStatus.FAILED));

        TRANSITIONS.put(PaymentStatus.SUCCESS, EnumSet.of(PaymentStatus.REFUNDED));

        TRANSITIONS.put(PaymentStatus.FAILED, EnumSet.noneOf(PaymentStatus.class));

        TRANSITIONS.put(PaymentStatus.REFUNDED, EnumSet.noneOf(PaymentStatus.class));
    }

    public void validateTransition(PaymentStatus from, PaymentStatus to) {

        if (from == to) {
            return;
        }
        Set<PaymentStatus> allowedTransitions = TRANSITIONS.getOrDefault(from, EnumSet.noneOf(PaymentStatus.class));

        if (!allowedTransitions.contains(to)) {
            throw new InvalidStateTransitionException(from.name(), to.name());
        }
    }

    public boolean canTransition(PaymentStatus from, PaymentStatus to) {
        if (from == to) {
            return true;
        }
        return TRANSITIONS.getOrDefault(from, EnumSet.noneOf(PaymentStatus.class)).contains(to);
    }

    public boolean isTerminal(PaymentStatus status) {
        return status == PaymentStatus.SUCCESS
                || status == PaymentStatus.FAILED
                || status == PaymentStatus.REFUNDED;
    }

    public int getStatusPriority(PaymentStatus status) {

        return switch (status) {
            case CREATED -> 0;
            case PENDING -> 1;
            case PROCESSING -> 2;
            case FAILED -> 3;
            case SUCCESS -> 4;
            case REFUNDED -> 5;

        };
    }
}