package com.portfolio.agent.turn.capability.portfolio.semantic;

import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.intelligence.retrieval.CorpusBackend;
import com.portfolio.agent.answer.intelligence.retrieval.SearchStrategy;
import com.portfolio.agent.turn.capability.portfolio.AuthorizedSubjectScope;
import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceInvocation;
import com.portfolio.agent.turn.planning.GoalKind;
import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.SemanticTaskParameters;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioSemanticResultFactoryTest {
    private final PortfolioSemanticResultFactory factory =
            new PortfolioSemanticResultFactory(new PortfolioSupportEvaluator());

    @Test
    void factProducesFullPartialAndNoResultFromRequestedFacetCoverage() {
        SemanticTask task = factTask();
        PortfolioEvidenceInvocation invocation = factInvocation();
        PortfolioSemanticResult full = factory.create(task, invocation,
                PortfolioSemanticFixtures.bundle(List.of(
                        PortfolioSemanticFixtures.unit(
                                "project-a", "background", AnswerClaimCategory.BACKGROUND),
                        PortfolioSemanticFixtures.unit(
                                "project-a", "verify", AnswerClaimCategory.VERIFICATION)),
                        "project-a")).orElseThrow();
        PortfolioSemanticResult partial = factory.create(task, invocation,
                PortfolioSemanticFixtures.bundle(List.of(
                        PortfolioSemanticFixtures.unit(
                                "project-a", "background", AnswerClaimCategory.BACKGROUND)),
                        "project-a")).orElseThrow();
        assertThat(full.getCoverage()).isEqualTo(PortfolioSemanticResult.Coverage.FULL);
        assertThat(partial.getCoverage()).isEqualTo(PortfolioSemanticResult.Coverage.PARTIAL);
        assertThat(factory.create(task, invocation,
                PortfolioSemanticFixtures.bundle(List.of(), "project-a"))).isEmpty();
    }

    private SemanticTask factTask() {
        return SemanticTask.of("task-fact", SemanticTask.Type.PORTFOLIO_FACT,
                new SemanticTaskParameters(GoalKind.PORTFOLIO_FACT,
                        new UserGoalProposal.PortfolioFactParameters(Set.of(
                                UserGoalProposal.Facet.BACKGROUND,
                                UserGoalProposal.Facet.VERIFICATION)), List.of()));
    }

    private PortfolioEvidenceInvocation factInvocation() {
        return new PortfolioEvidenceInvocation(
                SemanticTask.Type.PORTFOLIO_FACT,
                AuthorizedSubjectScope.allPublished("public-1"),
                List.of(PortfolioEvidenceInvocation.FacetProfile.BACKGROUND,
                        PortfolioEvidenceInvocation.FacetProfile.VERIFICATION), List.of(),
                "public-1", CorpusBackend.BUNDLE, SearchStrategy.EXACT, null, null);
    }
}
