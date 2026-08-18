package com.portfolio.agent.turn.planning;

import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;
import com.portfolio.agent.turn.lifecycle.ConversationWindow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoalInterpretationInputFactoryTest {

    private final GoalInterpretationInputFactory factory = new GoalInterpretationInputFactory();

    @Test
    void projectsOnlyFreeTextBoundedWindowAndReviewedSubjectDescriptors() {
        AgentTurnCommand.Ask command = new AgentTurnCommand.Ask(
                UUID.randomUUID(), new AgentTurnCommand.FreeText("介绍这个项目"),
                AgentTurnCommand.SurfaceContext.empty(),
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
