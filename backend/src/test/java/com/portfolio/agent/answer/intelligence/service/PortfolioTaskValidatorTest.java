package com.portfolio.agent.answer.intelligence.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRefinement;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTask;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PortfolioTaskValidatorTest {

    private final PortfolioTaskValidator validator = new PortfolioTaskValidator();

    @Test
    void requiresOnlyAudienceRoleForAnOtherwiseCompleteRecommendation() {
        PortfolioTask task = task(
                PortfolioTaskMode.RECOMMENDATION,
                new PortfolioConditions("BACKEND", null, Set.of("JAVA"), null, 2),
                null,
                null);

        PortfolioTaskValidation validation = validator.validate(task);

        assertThat(validation.isValid()).isFalse();
        assertThat(validation.getClarification().getMissingCondition()).isEqualTo("audienceRole");
    }

    @Test
    void acceptsARecommendationWithTheRequiredAudienceRole() {
        PortfolioTask task = task(
                PortfolioTaskMode.RECOMMENDATION,
                new PortfolioConditions("BACKEND", "INTERVIEWER", Set.of("JAVA"), null, 2),
                null,
                null);

        assertThat(validator.validate(task).isValid()).isTrue();
    }

    @Test
    void rejectsRefinementWithoutReturnedContextBeforeAnyRetrieval() {
        PortfolioTask task = task(
                PortfolioTaskMode.REFINE_RECOMMENDATION,
                PortfolioConditions.empty(),
                null,
                new PortfolioRefinement(PortfolioConditions.empty(), Set.of("project-a")));

        PortfolioTaskValidation validation = validator.validate(task);

        assertThat(validation.isValid()).isFalse();
        assertThat(validation.getClarification().getMissingCondition()).isEqualTo("recommendationContext");
    }

    @Test
    void rejectsARefinementThatDoesNotDescribeTheRequestedChange() {
        PortfolioTask task = task(
                PortfolioTaskMode.REFINE_RECOMMENDATION,
                PortfolioConditions.empty(),
                TestRecommendationContexts.context(),
                null);

        PortfolioTaskValidation validation = validator.validate(task);

        assertThat(validation.isValid()).isFalse();
        assertThat(validation.getClarification().getMissingCondition()).isEqualTo("refinement");
    }

    private PortfolioTask task(
            PortfolioTaskMode mode,
            PortfolioConditions conditions,
            com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationContext context,
            PortfolioRefinement refinement) {
        return new PortfolioTask("turn-1", "question", mode, 1.0d, conditions, context, refinement);
    }
}
