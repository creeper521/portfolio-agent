package com.portfolio.agent.answer.intelligence.domain;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioDecisionTest {

    @Test
    void notPortfolioDecisionCannotCarryAnswerMaterial() {
        PortfolioIntelligenceResult material = org.mockito.Mockito.mock(
                PortfolioIntelligenceResult.class);

        assertThatThrownBy(() -> new PortfolioDecision(
                PortfolioDisposition.NOT_PORTFOLIO,
                material))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not-portfolio");
    }

    @Test
    void handledDecisionRequiresAnswerMaterial() {
        assertThatThrownBy(() -> new PortfolioDecision(
                PortfolioDisposition.ANSWERED,
                null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("material");
    }

    @Test
    void exposesPrecisePublicSemanticsForMigration() {
        assertThat(AnswerResolution.values()).contains(
                AnswerResolution.NEEDS_CLARIFICATION,
                AnswerResolution.NOT_SUPPORTED,
                AnswerResolution.CAPABILITY_UNAVAILABLE);
        assertThat(ConversationAnswerScope.values()).contains(
                ConversationAnswerScope.GLOBAL,
                ConversationAnswerScope.MIXED);
        assertThat(AnswerIntentSource.values()).contains(
                AnswerIntentSource.PRESET,
                AnswerIntentSource.RULE,
                AnswerIntentSource.MODEL,
                AnswerIntentSource.REFERENCE,
                AnswerIntentSource.GLOBAL);
    }
}
