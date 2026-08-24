package com.portfolio.agent.turn.planning;

import com.portfolio.agent.turn.lifecycle.AgentTurnCommand;

import java.util.List;

public final class GoalInterpretationInputFactory {

    public GoalInterpretationInput create(
            AgentTurnCommand.Ask command,
            GoalResolutionContext context) {
        return create(command, context, null);
    }

    public GoalInterpretationInput create(
            AgentTurnCommand.Ask command,
            GoalResolutionContext context,
            com.portfolio.agent.turn.continuation.ConversationSemanticState
                    recentSemanticState) {
        if (!(command.getInput() instanceof AgentTurnCommand.FreeText freeText)) {
            throw new IllegalArgumentException("only free text uses goal interpretation");
        }
        List<String> recentMessages = command.getConversationWindow().getMessages().stream()
                .map(message -> message.getRole().name() + ":" + message.getText())
                .toList();
        return new GoalInterpretationInput(
                freeText.getText(), recentMessages,
                context.getPublicSubjects(), context.getAllowedGoalKinds(),
                GoalInterpretationInput.InterpretationMode.STANDARD,
                GoalInterpretationInput.DiscussionState.NONE, null, List.of(),
                java.util.Set.of(
                        SemanticRouteProposal.Route.STANDARD_GOAL,
                        SemanticRouteProposal.Route.NEEDS_CLARIFICATION),
                context.getAllowedRecommendationConstraints(),
                context.resolveHint(command.getSurfaceContext().getSubjectHint()),
                command.getSurfaceContext().getAudienceRole()
                        .map(value -> SemanticTaskParameters.AudienceProfile.valueOf(
                        value.name()))
                        .orElse(SemanticTaskParameters.AudienceProfile.GUEST),
                recentSemanticState);
    }
}
