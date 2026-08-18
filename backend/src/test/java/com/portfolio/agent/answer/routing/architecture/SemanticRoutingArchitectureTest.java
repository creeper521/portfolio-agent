package com.portfolio.agent.answer.routing.architecture;

import com.portfolio.agent.turn.lifecycle.MigrationAgentTurnRuntime;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticRoutingArchitectureTest {

    @Test
    void runtimeHasNoLegacyGlobalRoutingOrPortfolioResolutionDependency() {
        assertThat(Arrays.stream(MigrationAgentTurnRuntime.class.getDeclaredFields())
                .map(field -> field.getType().getSimpleName()))
                .doesNotContain(
                        "ConversationIntentRouter",
                        "PortfolioIntelligence",
                        "PortfolioIntelligenceAnswerAssembler",
                        "ConversationalModelPort",
                        "ConversationWindowManager",
                        "TurnRouter",
                        "PlanConfirmationService");
        assertThat(Arrays.stream(MigrationAgentTurnRuntime.class.getDeclaredFields())
                .map(field -> field.getType().getSimpleName()))
                .contains("GoalResolver", "SemanticPlanCompiler", "SemanticTurnEngine");
    }
}
