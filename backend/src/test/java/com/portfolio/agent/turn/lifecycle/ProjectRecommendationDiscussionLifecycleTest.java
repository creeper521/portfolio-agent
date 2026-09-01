package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.turn.capability.portfolio.AuthorizedSubjectScope;
import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceInvocation;
import com.portfolio.agent.turn.capability.portfolio.PortfolioSubjectKind;
import com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceBundle;
import com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerAchievementStatus;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimProjection;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimVerificationStatus;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerContributionType;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerKnowledge;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerMateriality;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerVerificationBasis;
import com.portfolio.agent.turn.capability.portfolio.knowledge.PortfolioKnowledgeGateway;
import com.portfolio.agent.turn.capability.portfolio.knowledge.RuntimeAnswerContent;
import com.portfolio.agent.turn.capability.portfolio.presentation.PortfolioPresentation;
import com.portfolio.agent.turn.capability.portfolio.retrieval.CorpusBackend;
import com.portfolio.agent.turn.capability.portfolio.retrieval.SearchStrategy;
import com.portfolio.agent.turn.capability.portfolio.semantic.PortfolioSemanticResult;
import com.portfolio.agent.turn.capability.portfolio.semantic.PortfolioSemanticResultFactory;
import com.portfolio.agent.turn.capability.portfolio.semantic.PortfolioSupportEvaluator;
import com.portfolio.agent.turn.continuation.ClarificationStore;
import com.portfolio.agent.turn.continuation.ContextMutationPlanner;
import com.portfolio.agent.turn.continuation.ContinuationContext;
import com.portfolio.agent.turn.continuation.ContinuationReference;
import com.portfolio.agent.turn.continuation.ConversationSessionResolver;
import com.portfolio.agent.turn.continuation.InMemoryConversationSessionStore;
import com.portfolio.agent.turn.execution.AnswerSectionType;
import com.portfolio.agent.turn.execution.GoalCoverage;
import com.portfolio.agent.turn.execution.PublicSourceReferenceValue;
import com.portfolio.agent.turn.execution.SemanticTurnEngine;
import com.portfolio.agent.turn.execution.SemanticTurnOutcome;
import com.portfolio.agent.turn.execution.TaskArtifact;
import com.portfolio.agent.turn.execution.TaskOutcome;
import com.portfolio.agent.turn.execution.TaskProvenance;
import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.turn.planning.GoalKind;
import com.portfolio.agent.turn.planning.GoalKnowledgeRequirement;
import com.portfolio.agent.turn.planning.GoalRequestedOutput;
import com.portfolio.agent.turn.planning.GoalResolver;
import com.portfolio.agent.turn.planning.GoalSubjectReference;
import com.portfolio.agent.turn.planning.ResolvedGoalSet;
import com.portfolio.agent.turn.planning.SemanticPlanCompiler;
import com.portfolio.agent.turn.planning.SemanticPlanValidator;
import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.SemanticTurnPlan;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import com.portfolio.agent.turn.projection.PublicAgentTurn;
import com.portfolio.agent.turn.projection.PublicAgentTurnProjector;
import com.portfolio.agent.turn.projection.PublicPresentation;
import com.portfolio.agent.turn.projection.SuggestedAction;
import com.portfolio.agent.turn.state.memory.InMemoryTurnExecutionStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectRecommendationDiscussionLifecycleTest {
    private static final Instant NOW = Instant.parse("2026-08-28T08:00:00Z");
    private static final String RELEASE = "public-1";

    @Test
    void actualRecommendationActionEntersAnActiveProjectDiscussion() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        InMemoryConversationSessionStore sessions = new InMemoryConversationSessionStore();
        ConversationSessionResolver sessionResolver = new ConversationSessionResolver(
                sessions, new byte[32], clock, Duration.ofMinutes(30));
        InMemoryTurnExecutionStore store = new InMemoryTurnExecutionStore(
                new ClarificationStore(clock, Duration.ofMinutes(5)),
                Duration.ofMinutes(30), sessions, clock);
        RuntimeAnswerContent content = content();
        PortfolioKnowledgeGateway knowledge = () -> content;
        GoalResolver resolver = mock(GoalResolver.class);
        when(resolver.resolve(any(), any(), any(), any()))
                .thenReturn(ResolvedGoalSet.goals(recommendationGoal()));
        SemanticTurnEngine engine = mock(SemanticTurnEngine.class);
        when(engine.execute(any(), any(), any(), anyBoolean(), any()))
                .thenAnswer(invocation -> outcome(
                        invocation.getArgument(0,
                                com.portfolio.agent.turn.planning
                                        .ValidatedSemanticTurnPlan.class)
                                .getPlan()));
        AgentTurnLifecycleService service = new AgentTurnLifecycleService(
                knowledge,
                resolver,
                new SemanticPlanCompiler(new SemanticPlanValidator()),
                engine,
                new PublicAgentTurnProjector(),
                new ContextMutationPlanner(() -> "recommendation_context_123"),
                store,
                new RequestFingerprintFactory(new byte[32]),
                sessionResolver,
                java.util.concurrent.ForkJoinPool.commonPool(),
                clock,
                Duration.ofSeconds(10),
                Duration.ofSeconds(5),
                Duration.ofSeconds(1),
                Duration.ofMinutes(10));

        AgentTurnLifecycleService.Result first = service.execute(
                null,
                new AgentTurnCommand.Ask(
                        UUID.randomUUID(),
                        AgentTurnCommand.ModelSelection.none(),
                        new AgentTurnCommand.FreeText("推荐项目"),
                        null,
                        null));

        assertThat(first.status()).isEqualTo(AgentTurnLifecycleService.Status.COMPLETED);
        assertThat(first.turn()).isInstanceOf(PublicAgentTurn.Answer.class);
        PublicPresentation.Recommendation presentation =
                (PublicPresentation.Recommendation) ((PublicAgentTurn.Answer) first.turn())
                        .getAnswer().getGoalResults().getFirst().getPresentation();
        SuggestedAction action = presentation.getItems().getFirst().getDiscussionAction();
        assertThat(action).isNotNull();
        ContinuationReference continuation = action.getContinuation();
        assertThat(continuation.getOperation())
                .isEqualTo(ContinuationReference.Operation.ENTER_RESULT);
        ContinuationContext.Recommendation stored =
                (ContinuationContext.Recommendation) store.findContext(
                        first.conversation().conversationId(),
                        continuation.getContextHandle(),
                        NOW,
                        TurnDeadline.after(Duration.ofSeconds(5), clock))
                        .orElseThrow();
        ContinuationContext.ResultItem selected = stored.getSelectedResults().stream()
                .filter(item -> item.resultItemId().equals(
                        continuation.getResultItemId()))
                .findFirst().orElseThrow();

        AgentTurnLifecycleService.Result entered = service.execute(
                first.conversation().resumeToken(),
                continueFrom(continuation));

        assertThat(entered.status()).isEqualTo(AgentTurnLifecycleService.Status.COMPLETED);
        assertThat(entered.turn()).isInstanceOf(PublicAgentTurn.Answer.class);
        assertThat(entered.conversation().discussion()).isNotNull();
        assertThat(entered.conversation().discussion().status())
                .isEqualTo(com.portfolio.agent.turn.continuation
                        .ActiveDiscussionPointer.Status.ACTIVE);
        assertThat(entered.conversation().discussion().projectId())
                .isEqualTo(selected.subjectId());
    }

    private AgentTurnCommand.Continue continueFrom(ContinuationReference continuation) {
        return new AgentTurnCommand.Continue(
                UUID.randomUUID(),
                AgentTurnCommand.ModelSelection.none(),
                AgentTurnCommand.ContinueOperation.ENTER_RESULT,
                continuation.getContextHandle(),
                continuation.getResultItemId(),
                null,
                null,
                null,
                null);
    }

    private UserGoalProposal recommendationGoal() {
        UserGoalProposal.InputAnchor anchor =
                new UserGoalProposal.InputAnchor("推荐项目", 0);
        return new UserGoalProposal(List.of(new UserGoalProposal.ProposedGoal(
                "recommend-projects",
                GoalKind.PORTFOLIO_RECOMMEND,
                anchor,
                List.of(),
                Set.of(GoalRequestedOutput.RECOMMENDATION),
                GoalKnowledgeRequirement.PUBLIC_PORTFOLIO_EVIDENCE,
                new UserGoalProposal.PortfolioRecommendationParameters(2, Set.of()))));
    }

    private SemanticTurnOutcome outcome(SemanticTurnPlan plan) {
        SemanticTask task = plan.getTasks().getFirst();
        if (task.getType() == SemanticTask.Type.PORTFOLIO_RECOMMEND) {
            return recommendationOutcome(plan, task);
        }
        return factOutcome(plan, task);
    }

    private SemanticTurnOutcome recommendationOutcome(
            SemanticTurnPlan plan, SemanticTask task) {
        AuthorizedSubjectScope scope = AuthorizedSubjectScope.allPublished(RELEASE);
        PortfolioEvidenceInvocation invocation = new PortfolioEvidenceInvocation(
                SemanticTask.Type.PORTFOLIO_RECOMMEND,
                scope,
                Set.of(PortfolioSubjectKind.PROJECT),
                List.of(PortfolioEvidenceInvocation.FacetProfile.RECOMMENDATION),
                List.of(),
                UserGoalProposal.Depth.STANDARD,
                2,
                Set.of(),
                RELEASE,
                CorpusBackend.BUNDLE,
                SearchStrategy.HYBRID,
                null,
                null);
        List<ValidatedEvidenceUnit> units = List.of(
                unit("project-a", "项目 A", "evidence-a"),
                unit("project-b", "项目 B", "evidence-b"));
        PortfolioSemanticResult.Recommendation result =
                (PortfolioSemanticResult.Recommendation)
                        new PortfolioSemanticResultFactory(
                                new PortfolioSupportEvaluator())
                                .create(task, invocation,
                                        new ValidatedEvidenceBundle(
                                                scope, RELEASE, units))
                                .orElseThrow();
        return produced(plan, task, result, units.getFirst(),
                TaskOutcome.Fulfillment.FULL,
                GoalCoverage.Coverage.FULL);
    }

    private SemanticTurnOutcome factOutcome(
            SemanticTurnPlan plan, SemanticTask task) {
        GoalSubjectReference subject = task.getSubjectReferences().getFirst();
        AuthorizedSubjectScope scope = AuthorizedSubjectScope.exact(
                List.of(subject), RELEASE);
        ValidatedEvidenceUnit unit = unit(
                subject.getReference(), "项目 A", "evidence-overview");
        PortfolioSemanticResult.Fact result = new PortfolioSemanticResult.Fact(
                PortfolioSemanticResult.Coverage.FULL,
                scope,
                List.of(unit),
                List.of(),
                UserGoalProposal.Depth.STANDARD);
        return produced(plan, task, result, unit,
                TaskOutcome.Fulfillment.FULL,
                GoalCoverage.Coverage.FULL);
    }

    private SemanticTurnOutcome produced(
            SemanticTurnPlan plan,
            SemanticTask task,
            PortfolioSemanticResult result,
            ValidatedEvidenceUnit unit,
            TaskOutcome.Fulfillment fulfillment,
            GoalCoverage.Coverage coverage) {
        PortfolioPresentation presentation = new PortfolioPresentation(
                "回答",
                List.of(new PortfolioPresentation.Section(
                        AnswerSectionType.SOLUTION,
                        "说明",
                        unit.getClaim().getStatement(),
                        List.of(unit.getSourceReference()))));
        TaskArtifact artifact = new TaskArtifact(
                result,
                presentation,
                new TaskProvenance(List.of(
                        unit.getSourceReference().getReferenceKey())));
        return new SemanticTurnOutcome(
                List.of(new TaskOutcome(
                        task.getTaskId(),
                        new TaskOutcome.Produced(artifact, fulfillment))),
                List.of(new GoalCoverage(
                        plan.getUserGoals().getFirst().getGoalId(),
                        coverage)));
    }

    private ValidatedEvidenceUnit unit(
            String subjectId, String title, String evidenceKey) {
        AnswerClaimProjection claim = new AnswerClaimProjection(
                "claim-" + evidenceKey,
                AnswerClaimCategory.IMPLEMENTATION,
                "公开实现证据 " + subjectId,
                "公开实现证据",
                AnswerAchievementStatus.DELIVERED,
                AnswerContributionType.PRIMARY,
                AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.VERIFIED,
                AnswerMateriality.KEY,
                List.of(evidenceKey));
        return new ValidatedEvidenceUnit(
                subjectId,
                PortfolioSubjectKind.PROJECT,
                title,
                null,
                Set.of(),
                claim,
                new PublicSourceReferenceValue(
                        evidenceKey,
                        "Evidence " + subjectId,
                        RELEASE,
                        "DOCUMENT",
                        "/projects/" + subjectId,
                        "/evidence/" + evidenceKey));
    }

    private RuntimeAnswerContent content() {
        RuntimeAnswerContent content = mock(RuntimeAnswerContent.class);
        AnswerKnowledge projectA = project("project-a", "项目 A");
        AnswerKnowledge projectB = project("project-b", "项目 B");
        when(content.getContentVersion()).thenReturn(RELEASE);
        when(content.getProjects()).thenReturn(List.of(projectA, projectB));
        when(content.getCases()).thenReturn(List.of());
        return content;
    }

    private AnswerKnowledge project(String id, String title) {
        AnswerKnowledge project = mock(AnswerKnowledge.class);
        when(project.getStableId()).thenReturn(id);
        when(project.getSlug()).thenReturn(id + "-slug");
        when(project.getTitle()).thenReturn(title);
        when(project.getCareerTrack()).thenReturn(null);
        when(project.getCapabilityCodes()).thenReturn(Set.of());
        return project;
    }
}
