package com.portfolio.agent.turn.infrastructure;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentRuntimePropertiesTest {

    @Test
    void defaultsFollowTheApprovedBudgetRelation() {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();

        properties.afterPropertiesSet();

        assertThat(properties.getTurnTimeout()).isEqualTo(Duration.ofSeconds(20));
        assertThat(properties.getSettlementReserve()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.getLeaseDuration()).isEqualTo(Duration.ofSeconds(35));
        assertThat(properties.getDatabaseOperationTimeout()).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    void rejectsReserveDatabaseAndLeaseBudgetsOutsideTheTurnRelation() {
        AgentRuntimeProperties reserve = new AgentRuntimeProperties();
        reserve.setSettlementReserve(Duration.ofSeconds(20));
        assertThatThrownBy(reserve::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class);

        AgentRuntimeProperties operation = new AgentRuntimeProperties();
        operation.setDatabaseOperationTimeout(Duration.ofSeconds(18));
        assertThatThrownBy(operation::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class);

        AgentRuntimeProperties lease = new AgentRuntimeProperties();
        lease.setLeaseDuration(Duration.ofSeconds(25));
        assertThatThrownBy(lease::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class);
    }
}
