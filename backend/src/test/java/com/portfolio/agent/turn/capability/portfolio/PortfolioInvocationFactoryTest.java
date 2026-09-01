package com.portfolio.agent.turn.capability.portfolio;

import com.portfolio.agent.infrastructure.model.ResolvedModelExecution;
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
import java.util.EnumSet;
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
                Set.of(PortfolioSubjectKind.PROJECT, PortfolioSubjectKind.CASE),
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
        assertThat(invocation.getAllowedSubjectKinds())
                .containsExactly(PortfolioSubjectKind.PROJECT);
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
        assertThat(invocation.getAllowedSubjectKinds())
                .containsExactly(PortfolioSubjectKind.PROJECT);
        assertThat(invocation.getPrimaryStrategy()).isEqualTo(SearchStrategy.HYBRID);
        assertThat(invocation.getFallbackBackend()).isNull();
        assertThat(invocation.getRequestedSize()).isEqualTo(3);
        assertThat(invocation.getRecommendationConstraints())
                .containsExactly("CAREER_TRACK_JAVA_BACKEND");
    }

    @Test
    void comparisonDerivesEveryAllowedKindFromTypedExactSubjects() {
        GoalSubjectReference project = new GoalSubjectReference(
                GoalSubjectReference.Kind.PROJECT, "project-a",
                GoalSubjectReference.Basis.SURFACE_HINT, null);
        GoalSubjectReference caseStudy = new GoalSubjectReference(
                GoalSubjectReference.Kind.CASE, "case-a",
                GoalSubjectReference.Basis.SURFACE_HINT, null);
        SemanticTask task = SemanticTask.of(
                "task-compare", SemanticTask.Type.PORTFOLIO_COMPARE,
                new SemanticTaskParameters(GoalKind.PORTFOLIO_COMPARE,
                        new UserGoalProposal.PortfolioCompareParameters(Set.of(
                                UserGoalProposal.PortfolioComparisonDimension.IMPLEMENTATION)),
                        List.of(project, caseStudy)),
                Set.of(GoalRequestedOutput.COMPARISON));

        PortfolioEvidenceInvocation invocation = new PortfolioInvocationFactory(
                CorpusBackend.BUNDLE).create(context(task));

        assertThat(invocation.getAllowedSubjectKinds()).containsExactlyInAnyOrder(
                PortfolioSubjectKind.PROJECT, PortfolioSubjectKind.CASE);
    }

    @Test
    void invocationDefensivelyCopiesAndFreezesAllowedKinds() {
        EnumSet<PortfolioSubjectKind> kinds = EnumSet.of(PortfolioSubjectKind.PROJECT);
        PortfolioEvidenceInvocation invocation = new PortfolioEvidenceInvocation(
                SemanticTask.Type.PORTFOLIO_FACT,
                AuthorizedSubjectScope.allPublished("public-1"), kinds,
                List.of(PortfolioEvidenceInvocation.FacetProfile.BACKGROUND), List.of(),
                "public-1", CorpusBackend.BUNDLE, SearchStrategy.EXACT, null, null);

        kinds.add(PortfolioSubjectKind.CASE);

        assertThat(invocation.getAllowedSubjectKinds())
                .containsExactly(PortfolioSubjectKind.PROJECT);
        assertThatThrownBy(() -> invocation.getAllowedSubjectKinds().add(
                PortfolioSubjectKind.CASE))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void recommendationInvocationRejectsAnyKindSetOtherThanProjectOnly() {
        assertThatThrownBy(() -> new PortfolioEvidenceInvocation(
                SemanticTask.Type.PORTFOLIO_RECOMMEND,
                AuthorizedSubjectScope.allPublished("public-1"),
                Set.of(PortfolioSubjectKind.PROJECT, PortfolioSubjectKind.CASE),
                List.of(PortfolioEvidenceInvocation.FacetProfile.RECOMMENDATION), List.of(),
                UserGoalProposal.Depth.STANDARD, 2, Set.of(), "public-1",
                CorpusBackend.BUNDLE, SearchStrategy.HYBRID, null, null))
                .isInstanceOf(PortfolioEvidenceCapability.PortfolioCapabilityException.class)
                .satisfies(failure -> assertThat(
                        ((PortfolioEvidenceCapability.PortfolioCapabilityException) failure)
                                .getFailure())
                        .isEqualTo(com.portfolio.agent.turn.capability.portfolio.retrieval
                                .RetrievalAttemptFailure.INTEGRITY_FAILURE))
                .satisfies(failure -> assertThat(
                        ((PortfolioEvidenceCapability.PortfolioCapabilityException) failure)
                                .getIntegrityReason())
                        .contains(PortfolioEvidenceCapability.IntegrityReason
                                .RECOMMENDATION_SUBJECT_KIND_CONTRACT_VIOLATION));

        assertThatThrownBy(() -> new PortfolioEvidenceInvocation(
                SemanticTask.Type.PORTFOLIO_RECOMMEND,
                AuthorizedSubjectScope.allPublished("public-1"),
                Set.of(),
                List.of(PortfolioEvidenceInvocation.FacetProfile.RECOMMENDATION), List.of(),
                UserGoalProposal.Depth.STANDARD, 2, Set.of(), "public-1",
                CorpusBackend.BUNDLE, SearchStrategy.HYBRID, null, null))
                .isInstanceOf(PortfolioEvidenceCapability.PortfolioCapabilityException.class);
    }

    @Test
    void unresolvedResultSubjectFailsClosedAsCapabilityIntegrityFailure() {
        GoalSubjectReference result = new GoalSubjectReference(
                GoalSubjectReference.Kind.RESULT, "result-item",
                GoalSubjectReference.Basis.CONTINUATION, null);
        SemanticTask task = SemanticTask.of(
                "task-result", SemanticTask.Type.PORTFOLIO_FACT,
                new SemanticTaskParameters(GoalKind.PORTFOLIO_FACT,
                        new UserGoalProposal.PortfolioFactParameters(
                                Set.of(UserGoalProposal.Facet.OVERVIEW),
                                UserGoalProposal.Depth.STANDARD),
                        List.of(result)),
                Set.of(GoalRequestedOutput.OVERVIEW));

        assertThatThrownBy(() -> new PortfolioInvocationFactory(
                CorpusBackend.BUNDLE).create(context(task)))
                .isInstanceOf(PortfolioEvidenceCapability.PortfolioCapabilityException.class)
                .satisfies(failure -> assertThat(
                        ((PortfolioEvidenceCapability.PortfolioCapabilityException) failure)
                                .getFailure())
                        .isEqualTo(com.portfolio.agent.turn.capability.portfolio.retrieval
                                .RetrievalAttemptFailure.INTEGRITY_FAILURE))
                .satisfies(failure -> assertThat(
                        ((PortfolioEvidenceCapability.PortfolioCapabilityException) failure)
                                .getIntegrityReason())
                        .contains(PortfolioEvidenceCapability.IntegrityReason
                                .UNRESOLVED_RESULT_SUBJECT));
    }

    @Test
    void audienceProfileChangesPortfolioFacetPriorityWithoutChangingTheEvidenceScope() {
        PortfolioEvidenceInvocation interviewer = invocationForOverview(
                UserGoalProposal.Depth.DETAILED,
                SemanticTaskParameters.AudienceProfile.INTERVIEWER);
        PortfolioEvidenceInvocation mentor = invocationForOverview(
                UserGoalProposal.Depth.DETAILED,
                SemanticTaskParameters.AudienceProfile.MENTOR);

        assertThat(interviewer.getFacets().getFirst())
                .isEqualTo(PortfolioEvidenceInvocation.FacetProfile.IMPLEMENTATION);
        assertThat(mentor.getFacets().getFirst())
                .isEqualTo(PortfolioEvidenceInvocation.FacetProfile.TECHNICAL_DECISION);
        assertThat(interviewer.getFacets()).containsExactlyInAnyOrderElementsOf(
                mentor.getFacets());
        assertThat(interviewer.getSubjectScope().getMode())
                .isEqualTo(mentor.getSubjectScope().getMode());
        assertThat(interviewer.getSubjectScope().getSubjects())
                .isEqualTo(mentor.getSubjectScope().getSubjects());
    }

    private TaskExecutionContext context(SemanticTask task) {
        return new TaskExecutionContext(
                task, List.of(), "public-1",
                TurnDeadline.after(Duration.ofSeconds(1), Clock.systemUTC()),
                new CancellationSignal(), false, false,
                ResolvedModelExecution.none());
    }

    private PortfolioEvidenceInvocation invocationForOverview(
            UserGoalProposal.Depth depth) {
        return invocationForOverview(depth, SemanticTaskParameters.AudienceProfile.GUEST);
    }

    private PortfolioEvidenceInvocation invocationForOverview(
            UserGoalProposal.Depth depth,
            SemanticTaskParameters.AudienceProfile audience) {
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
                        List.of(subject), audience),
                Set.of(GoalRequestedOutput.OVERVIEW));
        return new PortfolioInvocationFactory(CorpusBackend.BUNDLE).create(context(task));
    }
}
