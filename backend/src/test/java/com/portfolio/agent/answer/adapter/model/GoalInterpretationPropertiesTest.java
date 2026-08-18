package com.portfolio.agent.answer.adapter.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoalInterpretationPropertiesTest {

    @Test
    void hasOnlyTheBoundedGoalOperationTransportBudget() {
        GoalInterpretationProperties properties = new GoalInterpretationProperties();

        assertThat(properties.getTimeout()).isEqualTo(Duration.ofMillis(2500));
        assertThat(properties.getMaxOutputTokens()).isEqualTo(1600);
        properties.validate();
    }

    @Test
    void rejectsInvalidTransportBudgets() {
        GoalInterpretationProperties properties = new GoalInterpretationProperties();
        properties.setMaxOutputTokens(4001);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class);
    }
}
