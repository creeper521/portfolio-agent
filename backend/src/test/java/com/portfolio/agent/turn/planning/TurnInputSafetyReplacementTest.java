package com.portfolio.agent.turn.planning;

import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;
import com.portfolio.agent.turn.lifecycle.ConversationWindow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TurnInputSafetyReplacementTest {

    @Test
    void conversationWindowEnforcesCountCharacterAndRoleBoundaries() {
        List<ConversationWindow.Message> messages = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            messages.add(new ConversationWindow.Message(
                    index % 2 == 0 ? ConversationWindow.Role.USER : ConversationWindow.Role.ASSISTANT,
                    "x"));
        }
        assertThat(new ConversationWindow(messages).getMessages()).hasSize(40);
        messages.add(new ConversationWindow.Message(ConversationWindow.Role.USER, "x"));
        assertThatThrownBy(() -> new ConversationWindow(messages))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConversationWindow.Message(
                ConversationWindow.Role.USER, "x".repeat(4001)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConversationWindow(List.of(
                new ConversationWindow.Message(ConversationWindow.Role.ASSISTANT, "wrong"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownSurfaceHintFailsBeforeModelOrFallback() {
        GoalResolver resolver = new GoalResolver(
                input -> { throw new AssertionError("model must not receive unknown subject hint"); },
                command -> { throw new AssertionError("reviewed source must not receive unknown hint"); },
                new GoalInterpretationInputFactory(), new MinimalGoalFallback(), new GoalBoundaryPolicy());
        AgentTurnCommand command = new AgentTurnCommand.Ask(
                UUID.randomUUID(), new AgentTurnCommand.FreeText("这个项目如何实现"),
                new AgentTurnCommand.SurfaceContext(
                        new AgentTurnCommand.SubjectHint(
                                AgentTurnCommand.SubjectHintKind.PROJECT, "unknown-project"),
                        null, null), ConversationWindow.empty());
        GoalResolutionContext context = new GoalResolutionContext(
                List.of(new GoalInterpretationInput.PublicSubjectDescriptor(
                        GoalSubjectReference.Kind.PROJECT, "project-a", "Project A",
                        Set.of("project-a"))), Set.of(GoalKind.values()));

        assertThat(resolver.resolve(command, context).getKind())
                .isEqualTo(ResolvedGoalSet.Kind.INVALID_INPUT);
    }
}
