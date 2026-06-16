package com.payment.orchestrator.service;

import com.payment.orchestrator.domain.PaymentStatus;
import com.payment.orchestrator.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentStateMachineTest {

    private final PaymentStateMachine stateMachine = new PaymentStateMachine();

    @Test
    void allowsValidTransitions() {
        stateMachine.validateTransition(PaymentStatus.CREATED, PaymentStatus.PENDING);
        stateMachine.validateTransition(PaymentStatus.PENDING, PaymentStatus.PROCESSING);
        stateMachine.validateTransition(PaymentStatus.PROCESSING, PaymentStatus.SUCCESS);
        stateMachine.validateTransition(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED);
    }

    @ParameterizedTest
    @CsvSource({
            "SUCCESS, PENDING",
            "FAILED, SUCCESS",
            "REFUNDED, SUCCESS"
    })
    void rejectsInvalidTransitions(PaymentStatus from, PaymentStatus to) {
        assertThatThrownBy(() -> stateMachine.validateTransition(from, to))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void allowsSameStateTransition() {
        stateMachine.validateTransition(PaymentStatus.PENDING, PaymentStatus.PENDING);
        assertThat(stateMachine.canTransition(PaymentStatus.PENDING, PaymentStatus.PENDING)).isTrue();
    }
}
