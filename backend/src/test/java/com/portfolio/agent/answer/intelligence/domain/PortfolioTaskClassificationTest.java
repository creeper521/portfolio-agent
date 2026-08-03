package com.portfolio.agent.answer.intelligence.domain;

import com.portfolio.agent.answer.domain.ConversationIntent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioTaskClassificationTest {

    @Test
    void acceptsAnUnsafeBoundaryWithoutATaskMode() {
        PortfolioTaskClassification classification = new PortfolioTaskClassification(
                ConversationIntent.UNSUPPORTED_OR_UNSAFE,
                null,
                PortfolioConditions.empty(),
                null,
                0.95d);

        assertThat(classification.getBoundaryIntent())
                .isEqualTo(ConversationIntent.UNSUPPORTED_OR_UNSAFE);
        assertThat(classification.getMode()).isNull();
    }

    @Test
    void rejectsBoundaryIntentOutsideTheClosedSafetyAndTimeSet() {
        assertThatThrownBy(() -> new PortfolioTaskClassification(
                ConversationIntent.GENERAL_KNOWLEDGE,
                null,
                PortfolioConditions.empty(),
                null,
                0.95d))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("boundaryIntent");
    }

    @Test
    void rejectsClassificationContainingBothBoundaryAndTask() {
        assertThatThrownBy(() -> new PortfolioTaskClassification(
                ConversationIntent.TIME_SENSITIVE,
                PortfolioTaskMode.COMPARISON,
                PortfolioConditions.empty(),
                null,
                0.95d))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one");
    }

    @Test
    void routingDecisionContainsExactlyOneBoundaryOrTask() {
        PortfolioTask task = new PortfolioTask(
                "turn-1",
                "Compare these projects",
                PortfolioTaskMode.COMPARISON,
                1.0d,
                PortfolioConditions.empty(),
                null,
                null);

        PortfolioTaskRoutingDecision boundary = PortfolioTaskRoutingDecision.boundary(
                ConversationIntent.TIME_SENSITIVE);
        PortfolioTaskRoutingDecision taskDecision = PortfolioTaskRoutingDecision.task(task);

        assertThat(boundary.getBoundaryIntent()).isEqualTo(ConversationIntent.TIME_SENSITIVE);
        assertThat(boundary.getTask()).isNull();
        assertThat(taskDecision.getBoundaryIntent()).isNull();
        assertThat(taskDecision.getTask()).isSameAs(task);
        assertThatThrownBy(() -> PortfolioTaskRoutingDecision.boundary(
                ConversationIntent.GENERAL_KNOWLEDGE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
