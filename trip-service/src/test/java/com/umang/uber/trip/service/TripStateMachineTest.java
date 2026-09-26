package com.umang.uber.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.umang.uber.common.enums.TripStatus;
import org.junit.jupiter.api.Test;

class TripStateMachineTest {

    @Test
    void happyPathTransitionsAreLegal() {
        assertThat(TripStateMachine.canTransition(TripStatus.MATCHED, TripStatus.EN_ROUTE)).isTrue();
        assertThat(TripStateMachine.canTransition(TripStatus.EN_ROUTE, TripStatus.ONGOING)).isTrue();
        assertThat(TripStateMachine.canTransition(TripStatus.ONGOING, TripStatus.COMPLETED)).isTrue();
    }

    @Test
    void cancellationLegalFromEarlyStatesOnly() {
        assertThat(TripStateMachine.canTransition(TripStatus.MATCHED, TripStatus.CANCELLED)).isTrue();
        assertThat(TripStateMachine.canTransition(TripStatus.EN_ROUTE, TripStatus.CANCELLED)).isTrue();
        // Cannot cancel a trip already underway or finished.
        assertThat(TripStateMachine.canTransition(TripStatus.ONGOING, TripStatus.CANCELLED)).isFalse();
        assertThat(TripStateMachine.canTransition(TripStatus.COMPLETED, TripStatus.CANCELLED)).isFalse();
    }

    @Test
    void illegalJumpsAreRejected() {
        // Skipping steps is not allowed.
        assertThat(TripStateMachine.canTransition(TripStatus.MATCHED, TripStatus.ONGOING)).isFalse();
        assertThat(TripStateMachine.canTransition(TripStatus.MATCHED, TripStatus.COMPLETED)).isFalse();
        // Terminal states have no outgoing transitions.
        assertThat(TripStateMachine.canTransition(TripStatus.COMPLETED, TripStatus.ONGOING)).isFalse();
        assertThat(TripStateMachine.canTransition(TripStatus.CANCELLED, TripStatus.MATCHED)).isFalse();
    }

    @Test
    void assertCanTransition_throwsOnIllegal() {
        assertThatThrownBy(() ->
                TripStateMachine.assertCanTransition(TripStatus.COMPLETED, TripStatus.ONGOING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Illegal trip transition");
    }

    @Test
    void assertCanTransition_passesOnLegal() {
        // Should not throw.
        TripStateMachine.assertCanTransition(TripStatus.EN_ROUTE, TripStatus.ONGOING);
    }
}
