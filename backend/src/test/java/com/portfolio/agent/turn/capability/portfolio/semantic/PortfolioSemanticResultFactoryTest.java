package com.portfolio.agent.turn.capability.portfolio.semantic;

import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory;
import com.portfolio.agent.turn.capability.portfolio.retrieval.CorpusBackend;
import com.portfolio.agent.turn.capability.portfolio.retrieval.SearchStrategy;
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

    @Test
    void recommendationRanksConstraintMatchesAndReportsUnsatisfiedFallbacks() {
        SemanticTask task = recommendationTask();
        PortfolioEvidenceInvocation invocation = new PortfolioEvidenceInvocation(
                SemanticTask.Type.PORTFOLIO_RECOMMEND,
                AuthorizedSubjectScope.allPublished("public-1"),
                List.of(PortfolioEvidenceInvocation.FacetProfile.RECOMMENDATION), List.of(),
                UserGoalProposal.Depth.STANDARD, 2,
                Set.of("CAREER_TRACK_JAVA_BACKEND", "CAPABILITY_SQL"),
                "public-1", CorpusBackend.BUNDLE, SearchStrategy.HYBRID, null, null);
        PortfolioSemanticResult.Recommendation result =
                (PortfolioSemanticResult.Recommendation) factory.create(
                        task, invocation, PortfolioSemanticFixtures.bundle(List.of(
                                recommendationUnit("project-agent", "AGENT", Set.of("AGENT")),
                                recommendationUnit("project-sql", "JAVA_BACKEND", Set.of("SQL"))),
                                "project-agent", "project-sql")).orElseThrow();

        assertThat(result.getSelectedSubjectIds())
                .containsExactly("project-sql", "project-agent");
        assertThat(result.getCoverage()).isEqualTo(PortfolioSemanticResult.Coverage.PARTIAL);
        assertThat(result.getUnsatisfiedConstraints())
                .containsExactlyInAnyOrder(
                        "CAREER_TRACK_JAVA_BACKEND", "CAPABILITY_SQL");
        assertThat(result.getItems().getFirst().reasonCodes())
                .contains(
                        PortfolioSemanticResult.Recommendation.RecommendationReasonCode
                                .CAREER_TRACK_MATCH,
                        PortfolioSemanticResult.Recommendation.RecommendationReasonCode
                                .CAPABILITY_MATCH);
    }

    private com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit
            recommendationUnit(String subjectId, String careerTrack, Set<String> capabilities) {
        com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit base =
                PortfolioSemanticFixtures.unit(
                        subjectId, "implementation-" + subjectId,
                        AnswerClaimCategory.IMPLEMENTATION);
        return new com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit(
                subjectId, "标题 " + subjectId, careerTrack, capabilities,
                base.getClaim(), base.getSourceReference());
    }

    private SemanticTask recommendationTask() {
        return SemanticTask.of("task-recommend", SemanticTask.Type.PORTFOLIO_RECOMMEND,
                new SemanticTaskParameters(GoalKind.PORTFOLIO_RECOMMEND,
                        new UserGoalProposal.PortfolioRecommendationParameters(
                                2, Set.of("CAREER_TRACK_JAVA_BACKEND", "CAPABILITY_SQL")),
                        List.of()));
    }

    private SemanticTask factTask() {
        return SemanticTask.of("task-fact", SemanticTask.Type.PORTFOLIO_FACT,
                new SemanticTaskParameters(GoalKind.PORTFOLIO_FACT,
                        new UserGoalProposal.PortfolioFactParameters(Set.of(
                                UserGoalProposal.Facet.BACKGROUND,
                                UserGoalProposal.Facet.VERIFICATION),
                                UserGoalProposal.Depth.STANDARD), List.of()));
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
