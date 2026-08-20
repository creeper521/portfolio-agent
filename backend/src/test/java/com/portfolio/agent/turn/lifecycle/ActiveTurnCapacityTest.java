package com.portfolio.agent.turn.lifecycle;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActiveTurnCapacityTest {

    @Test
    void ninthActiveTurnIsRejectedAndClosingALeaseRestoresCapacity() {
        ActiveTurnCapacity capacity = new ActiveTurnCapacity(8);
        ActiveTurnCapacity.Lease[] leases = new ActiveTurnCapacity.Lease[8];
        for (int index = 0; index < leases.length; index++) {
            leases[index] = capacity.acquire();
        }

        assertThatThrownBy(capacity::acquire)
                .isInstanceOfSatisfying(AgentAdmissionRejectedException.class, rejection -> {
                    assertThat(rejection.getReason()).isEqualTo(
                            AgentAdmissionRejectedException.RejectionReason.GLOBAL_ACTIVE_TURN_LIMIT);
                    assertThat(rejection.getRetryAfterSeconds()).isEqualTo(1);
                });

        leases[0].close();
        leases[0].close();
        try (ActiveTurnCapacity.Lease ignored = capacity.acquire()) {
            assertThat(ignored).isNotNull();
        }
        for (int index = 1; index < leases.length; index++) {
            leases[index].close();
        }
    }

    @Test
    void invalidCapacityIsRejectedAtConstruction() {
        assertThatThrownBy(() -> new ActiveTurnCapacity(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
