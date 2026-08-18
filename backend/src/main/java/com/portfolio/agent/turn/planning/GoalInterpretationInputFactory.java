package com.portfolio.agent.turn.planning;

import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;

import java.util.List;

public final class GoalInterpretationInputFactory {

    public GoalInterpretationInput create(
            AgentTurnCommand.Ask command,
            GoalResolutionContext context) {
        if (!(command.getInput() instanceof AgentTurnCommand.FreeText freeText)) {
            throw new IllegalArgumentException("only free text uses goal interpretation");
        }
        List<String> recentMessages = command.getConversationWindow().getMessages().stream()
                .map(message -> message.getRole().name() + ":" + message.getText())
                .toList();
        return new GoalInterpretationInput(
                freeText.getText(), recentMessages,
                context.getPublicSubjects(), context.getAllowedGoalKinds());
    }
}
