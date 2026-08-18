package com.portfolio.agent.turn.planning;

import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;
import com.portfolio.agent.turn.lifecycle.ConversationWindow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class GoalResolverTest {

    @Test
    void freeTextCallsOnlyGoalInterpretationPortOnce() {
        AtomicInteger modelCalls = new AtomicInteger();
        AtomicInteger reviewedCalls = new AtomicInteger();
        UserGoalProposal proposal = generalProposal(GoalKnowledgeRequirement.STABLE_GENERAL_EXPLANATION);
        GoalResolver resolver = resolver(input -> {
            modelCalls.incrementAndGet();
            return GoalInterpretationResult.goals(proposal);
        }, command -> {
            reviewedCalls.incrementAndGet();
            return proposal;
        });

        ResolvedGoalSet result = resolver.resolve(freeText("解释幂等"), context());

        assertThat(result.getKind()).isEqualTo(ResolvedGoalSet.Kind.GOALS);
        assertThat(result.getGoalProposal()).contains(proposal);
        assertThat(modelCalls).hasValue(1);
        assertThat(reviewedCalls).hasValue(0);
    }

    @Test
    void presetContinueAndClarificationUseReviewedSourceWithoutModelCall() {
        AtomicInteger modelCalls = new AtomicInteger();
        AtomicInteger reviewedCalls = new AtomicInteger();
        UserGoalProposal proposal = generalProposal(GoalKnowledgeRequirement.STABLE_GENERAL_EXPLANATION);
        GoalResolver resolver = resolver(input -> {
            modelCalls.incrementAndGet();
            return GoalInterpretationResult.goals(proposal);
        }, command -> {
            reviewedCalls.incrementAndGet();
            return proposal;
        });

        assertThat(resolver.resolve(preset(), context()).getKind()).isEqualTo(ResolvedGoalSet.Kind.GOALS);
        assertThat(resolver.resolve(continuation(), context()).getKind()).isEqualTo(ResolvedGoalSet.Kind.GOALS);
        assertThat(resolver.resolve(clarification(), context()).getKind()).isEqualTo(ResolvedGoalSet.Kind.GOALS);
        assertThat(modelCalls).hasValue(0);
        assertThat(reviewedCalls).hasValue(3);
    }

    @Test
    void providerFailureWithoutExactReviewedAliasIsCapabilityUnavailable() {
        GoalResolver resolver = resolver(input -> {
            throw new GoalInterpretationUnavailableException();
        }, command -> generalProposal(GoalKnowledgeRequirement.STABLE_GENERAL_EXPLANATION));

        ResolvedGoalSet result = resolver.resolve(freeText("请随便介绍一下"), context());

        assertThat(result.getKind()).isEqualTo(ResolvedGoalSet.Kind.CAPABILITY_UNAVAILABLE);
    }

    @Test
    void providerFailureMayUseExactReviewedPublicAliasOnly() {
        GoalResolver resolver = resolver(input -> {
            throw new GoalInterpretationUnavailableException();
        }, command -> generalProposal(GoalKnowledgeRequirement.STABLE_GENERAL_EXPLANATION));

        ResolvedGoalSet result = resolver.resolve(freeText("SQL 审计项目"), context());

        assertThat(result.getKind()).isEqualTo(ResolvedGoalSet.Kind.GOALS);
        assertThat(result.getGoalProposal().orElseThrow().getGoals().get(0).getGoalKind())
                .isEqualTo(GoalKind.PORTFOLIO_FACT);
    }

    @Test
    void semanticAmbiguityRemainsClarificationInsteadOfCapabilityFailure() {
        ClarificationProposal clarification = new ClarificationProposal(
                ClarificationProposal.Field.SUBJECT, "请选择项目",
                new UserGoalProposal.InputAnchor("这个项目", 0));
        GoalResolver resolver = resolver(
                input -> GoalInterpretationResult.clarification(clarification),
                command -> generalProposal(GoalKnowledgeRequirement.STABLE_GENERAL_EXPLANATION));

        ResolvedGoalSet result = resolver.resolve(freeText("这个项目怎么样"), context());

        assertThat(result.getKind()).isEqualTo(ResolvedGoalSet.Kind.CLARIFICATION);
        assertThat(result.getClarification()).contains(clarification);
    }

    private GoalResolver resolver(
            GoalInterpretationPort port, ReviewedGoalSource reviewedGoalSource) {
        return new GoalResolver(
                port, reviewedGoalSource, new GoalInterpretationInputFactory(),
                new MinimalGoalFallback(), new GoalBoundaryPolicy());
    }

    private GoalResolutionContext context() {
        return new GoalResolutionContext(
                List.of(new GoalInterpretationInput.PublicSubjectDescriptor(
                        GoalSubjectReference.Kind.PROJECT, "sql-audit", "SQL 审计项目")),
                Set.of(GoalKind.values()));
    }

    private AgentTurnCommand freeText(String text) {
        return new AgentTurnCommand.Ask(UUID.randomUUID(), new AgentTurnCommand.FreeText(text),
                AgentTurnCommand.SurfaceContext.empty(), ConversationWindow.empty());
    }

    private AgentTurnCommand preset() {
        return new AgentTurnCommand.Ask(UUID.randomUUID(),
                new AgentTurnCommand.Preset("question-sql-audit", "pcv1-0123456789abcdef"),
                AgentTurnCommand.SurfaceContext.empty(), ConversationWindow.empty());
    }

    private AgentTurnCommand continuation() {
        return new AgentTurnCommand.Continue(UUID.randomUUID(), "context_opaque", null, "继续",
                AgentTurnCommand.SurfaceContext.empty(), ConversationWindow.empty());
    }

    private AgentTurnCommand clarification() {
        return new AgentTurnCommand.ResolveClarification(
                UUID.randomUUID(), "clarification_opaque",
                new AgentTurnCommand.ChoiceAnswer("choice_opaque"),
                AgentTurnCommand.SurfaceContext.empty(), ConversationWindow.empty());
    }

    static UserGoalProposal generalProposal(GoalKnowledgeRequirement requirement) {
        return new UserGoalProposal(List.of(new UserGoalProposal.ProposedGoal(
                "general-goal", GoalKind.GENERAL_EXPLANATION,
                new UserGoalProposal.InputAnchor("幂等", 0), List.of(),
                Set.of(GoalRequestedOutput.EXPLANATION), requirement,
                new UserGoalProposal.GeneralExplanationParameters(
                        new UserGoalProposal.InputAnchor("幂等", 0),
                        UserGoalProposal.Depth.STANDARD))));
    }
}
