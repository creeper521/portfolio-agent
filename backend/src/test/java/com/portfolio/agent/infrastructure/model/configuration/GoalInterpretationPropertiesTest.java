package com.portfolio.agent.infrastructure.model.configuration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoalInterpretationPropertiesTest {

    @Test
    void hasOnlyTheBoundedGoalOutputBudget() {
        GoalInterpretationProperties properties = new GoalInterpretationProperties();

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
