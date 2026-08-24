package com.portfolio.agent.turn.planning;

import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;
import com.portfolio.agent.turn.lifecycle.ConversationWindow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.time.Instant;
import com.portfolio.agent.turn.continuation.ConversationSemanticState;
import com.portfolio.agent.turn.execution.AnswerSectionType;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoalInterpretationInputFactoryTest {

    private final GoalInterpretationInputFactory factory = new GoalInterpretationInputFactory();

    @Test void carriesServerTypedSemanticStateSeparatelyFromClientMessages() {
        ConversationSemanticState state = new ConversationSemanticState(
                "public-1", List.of(new ConversationSemanticState.GoalSummary(
                "goal-1", GoalKind.PORTFOLIO_FACT,
                List.of(new ConversationSemanticState.Subject(
                        GoalSubjectReference.Kind.PROJECT, "project-1")),
                Set.of(GoalRequestedOutput.SOLUTION),
                Set.of(UserGoalProposal.Facet.SOLUTION),
                UserGoalProposal.Depth.STANDARD, Set.of(), null, Set.of(),
                List.of(new ConversationSemanticState.SectionReference(
                        "section-goal-1-1", AnswerSectionType.SOLUTION)))),
                Instant.parse("2026-08-24T05:00:00Z"));
        AgentTurnCommand.Ask command = new AgentTurnCommand.Ask(
                java.util.UUID.randomUUID(), new AgentTurnCommand.FreeText("进一步展开"),
                null, AgentTurnCommand.SurfaceContext.empty(), ConversationWindow.empty());

        GoalInterpretationInput input = factory.create(
                command, new GoalResolutionContext(
                        List.of(new GoalInterpretationInput.PublicSubjectDescriptor(
                                GoalSubjectReference.Kind.PROJECT,
                                "project-1", "公开项目")),
                        Set.of(GoalKind.PORTFOLIO_FACT)), state);

        assertThat(input.getRecentMessages()).isEmpty();
        assertThat(input.getRecentSemanticState()).isSameAs(state);
        assertThat(input.recentPortfolioSubject().getReference())
                .isEqualTo("project-1");
    }

    @Test
    void projectsOnlyFreeTextBoundedWindowAndReviewedSubjectDescriptors() {
        AgentTurnCommand.Ask command = new AgentTurnCommand.Ask(
                UUID.randomUUID(), new AgentTurnCommand.FreeText("介绍这个项目"),
                new AgentTurnCommand.SurfaceContext(
                        new AgentTurnCommand.SubjectHint(
                                AgentTurnCommand.SubjectHintKind.PROJECT, "sql-audit"),
                        AgentTurnCommand.AudienceRole.INTERVIEWER,
                        AgentTurnCommand.RequestSource.PROJECT),
                new ConversationWindow(List.of(
                        new ConversationWindow.Message(ConversationWindow.Role.USER, "上一问"),
                        new ConversationWindow.Message(ConversationWindow.Role.ASSISTANT, "公开回答摘要"))));
        GoalResolutionContext context = new GoalResolutionContext(
                List.of(new GoalInterpretationInput.PublicSubjectDescriptor(
                        GoalSubjectReference.Kind.PROJECT, "sql-audit", "SQL 审计项目")),
                Set.of(GoalKind.PORTFOLIO_FACT));

        GoalInterpretationInput input = factory.create(command, context);

        assertThat(input.getUserText()).isEqualTo("介绍这个项目");
        assertThat(input.getRecentMessages()).containsExactly(
                "USER:上一问", "ASSISTANT:公开回答摘要");
        assertThat(input.getPublicSubjects()).extracting(
                GoalInterpretationInput.PublicSubjectDescriptor::getReference)
                .containsExactly("sql-audit");
        assertThat(input.getAllowedGoalKinds()).containsExactly(GoalKind.PORTFOLIO_FACT);
        assertThat(input.getDefaultSubject()).isNotNull();
        assertThat(input.getDefaultSubject().getReference()).isEqualTo("sql-audit");
        assertThat(input.getAudienceProfile())
                .isEqualTo(SemanticTaskParameters.AudienceProfile.INTERVIEWER);
    }

    @Test
    void refusesToSendPresetThroughModelInterpretation() {
        AgentTurnCommand.Ask preset = new AgentTurnCommand.Ask(
                UUID.randomUUID(),
                new AgentTurnCommand.Preset("question-sql-audit", "pcv1-0123456789abcdef"),
                AgentTurnCommand.SurfaceContext.empty(), ConversationWindow.empty());

        assertThatThrownBy(() -> factory.create(preset, new GoalResolutionContext(
                List.of(), Set.of(GoalKind.values()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only free text");
    }
}
