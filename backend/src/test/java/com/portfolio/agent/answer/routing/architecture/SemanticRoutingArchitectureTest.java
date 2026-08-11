package com.portfolio.agent.answer.routing.architecture;

import com.portfolio.agent.answer.service.ConversationalAgentRuntime;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticRoutingArchitectureTest {

    @Test
    void runtimeHasNoLegacyGlobalRoutingOrPortfolioResolutionDependency() {
        assertThat(Arrays.stream(ConversationalAgentRuntime.class.getDeclaredFields())
                .map(field -> field.getType().getSimpleName()))
                .doesNotContain(
                        "ConversationIntentRouter",
                        "PortfolioIntelligence",
                        "PortfolioIntelligenceAnswerAssembler",
                        "ConversationalModelPort",
                        "ConversationWindowManager");
    }
}
