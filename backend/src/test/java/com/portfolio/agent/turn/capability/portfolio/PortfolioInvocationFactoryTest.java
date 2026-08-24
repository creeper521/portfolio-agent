package com.portfolio.agent.turn.capability.portfolio;

import com.portfolio.agent.turn.capability.portfolio.retrieval.CorpusBackend;
import com.portfolio.agent.turn.capability.portfolio.retrieval.SearchStrategy;
import com.portfolio.agent.turn.execution.CancellationSignal;
import com.portfolio.agent.turn.execution.TaskExecutionContext;
import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.turn.planning.GoalKind;
import com.portfolio.agent.turn.planning.GoalRequestedOutput;
import com.portfolio.agent.turn.planning.GoalSubjectReference;
import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.SemanticTaskParameters;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioInvocationFactoryTest {
    @Test
    void portfolioOverviewDepthChangesRetrievalProfilesAndBounds() {
        PortfolioEvidenceInvocation concise = invocationForOverview(
                UserGoalProposal.Depth.CONCISE);
        PortfolioEvidenceInvocation detailed = invocationForOverview(
                UserGoalProposal.Depth.DETAILED);

        assertThat(concise.getFacets()).containsExactly(
                PortfolioEvidenceInvocation.FacetProfile.BACKGROUND,
                PortfolioEvidenceInvocation.FacetProfile.OUTCOME);
        assertThat(concise.getMaximumEvidenceUnitsPerSubject()).isEqualTo(2);
        assertThat(detailed.getFacets()).containsExactly(
                PortfolioEvidenceInvocation.FacetProfile.BACKGROUND,
                PortfolioEvidenceInvocation.FacetProfile.RESPONSIBILITY,
                PortfolioEvidenceInvocation.FacetProfile.IMPLEMENTATION,
                PortfolioEvidenceInvocation.FacetProfile.TECHNICAL_DECISION,
                PortfolioEvidenceInvocation.FacetProfile.VERIFICATION,
                PortfolioEvidenceInvocation.FacetProfile.OUTCOME,
                PortfolioEvidenceInvocation.FacetProfile.LIMITATION);
        assertThat(detailed.getMaximumEvidenceUnitsPerSubject()).isEqualTo(12);
    }

    @Test
    void invocationRejectsUnknownComparisonDimensionInsteadOfDefaultingToVerification() {
        assertThatThrownBy(() -> new PortfolioEvidenceInvocation(
                SemanticTask.Type.PORTFOLIO_COMPARE,
                AuthorizedSubjectScope.allPublished("public-1"),
                List.of(), List.of("INVENTED"), "public-1",
                CorpusBackend.BUNDLE, SearchStrategy.EXACT, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported portfolio comparison dimension");
    }

    @Test
    void compilesTaskScopeProfilesReleaseAndFallbackExactlyOnce() {
        UserGoalProposal.InputAnchor subjectAnchor =
                new UserGoalProposal.InputAnchor("project-a", 0);
        GoalSubjectReference subject = new GoalSubjectReference(
                GoalSubjectReference.Kind.PROJECT, "project-a",
                GoalSubjectReference.Basis.EXPLICIT_INPUT, subjectAnchor);
        SemanticTask task = SemanticTask.of(
                "task-fact", SemanticTask.Type.PORTFOLIO_FACT,
                new SemanticTaskParameters(GoalKind.PORTFOLIO_FACT,
                        new UserGoalProposal.PortfolioFactParameters(Set.of(
                                UserGoalProposal.Facet.SOLUTION,
                                UserGoalProposal.Facet.STATUS),
                                UserGoalProposal.Depth.STANDARD), List.of(subject)),
                Set.of(GoalRequestedOutput.OVERVIEW));
        PortfolioEvidenceInvocation invocation = new PortfolioInvocationFactory(
                CorpusBackend.POSTGRESQL).create(context(task));

        assertThat(invocation.getSubjectScope().getMode())
                .isEqualTo(AuthorizedSubjectScope.Mode.EXACT);
        assertThat(invocation.getSubjectScope().getSubjects()).extracting(
                        AuthorizedSubjectScope.Subject::getReference)
                .containsExactly("project-a");
        assertThat(invocation.getFacets()).containsExactly(
                PortfolioEvidenceInvocation.FacetProfile.IMPLEMENTATION,
                PortfolioEvidenceInvocation.FacetProfile.TECHNICAL_DECISION,
                PortfolioEvidenceInvocation.FacetProfile.OUTCOME,
                PortfolioEvidenceInvocation.FacetProfile.LIMITATION);
        assertThat(invocation.getContentReleaseId()).isEqualTo("public-1");
        assertThat(invocation.getPrimaryBackend()).isEqualTo(CorpusBackend.POSTGRESQL);
        assertThat(invocation.getPrimaryStrategy()).isEqualTo(SearchStrategy.EXACT);
        assertThat(invocation.getFallbackBackend()).isEqualTo(CorpusBackend.BUNDLE);
        assertThat(invocation.getFallbackStrategy()).isEqualTo(SearchStrategy.EXACT);
    }

    @Test
    void recommendationWithoutExplicitSubjectsUsesAllPublishedHybridScope() {
        SemanticTask task = SemanticTask.of(
                "task-recommend", SemanticTask.Type.PORTFOLIO_RECOMMEND,
                new SemanticTaskParameters(GoalKind.PORTFOLIO_RECOMMEND,
                        new UserGoalProposal.PortfolioRecommendationParameters(
                                3, Set.of("CAREER_TRACK_JAVA_BACKEND")), List.of()),
                Set.of(GoalRequestedOutput.RECOMMENDATION));
        PortfolioEvidenceInvocation invocation = new PortfolioInvocationFactory(
                CorpusBackend.BUNDLE).create(context(task));

        assertThat(invocation.getSubjectScope().getMode())
                .isEqualTo(AuthorizedSubjectScope.Mode.ALL_PUBLISHED);
        assertThat(invocation.getPrimaryStrategy()).isEqualTo(SearchStrategy.HYBRID);
        assertThat(invocation.getFallbackBackend()).isNull();
        assertThat(invocation.getRequestedSize()).isEqualTo(3);
        assertThat(invocation.getRecommendationConstraints())
                .containsExactly("CAREER_TRACK_JAVA_BACKEND");
    }

    private TaskExecutionContext context(SemanticTask task) {
        return new TaskExecutionContext(
                task, List.of(), "public-1",
                TurnDeadline.after(Duration.ofSeconds(1), Clock.systemUTC()),
                new CancellationSignal(), false, false);
    }

    private PortfolioEvidenceInvocation invocationForOverview(
            UserGoalProposal.Depth depth) {
        UserGoalProposal.InputAnchor subjectAnchor =
                new UserGoalProposal.InputAnchor("project-a", 0);
        GoalSubjectReference subject = new GoalSubjectReference(
                GoalSubjectReference.Kind.PROJECT, "project-a",
                GoalSubjectReference.Basis.EXPLICIT_INPUT, subjectAnchor);
        SemanticTask task = SemanticTask.of(
                "task-overview-" + depth.name().toLowerCase(),
                SemanticTask.Type.PORTFOLIO_FACT,
                new SemanticTaskParameters(GoalKind.PORTFOLIO_FACT,
                        new UserGoalProposal.PortfolioFactParameters(
                                Set.of(UserGoalProposal.Facet.OVERVIEW), depth),
                        List.of(subject)),
                Set.of(GoalRequestedOutput.OVERVIEW));
        return new PortfolioInvocationFactory(CorpusBackend.BUNDLE).create(context(task));
    }
}
