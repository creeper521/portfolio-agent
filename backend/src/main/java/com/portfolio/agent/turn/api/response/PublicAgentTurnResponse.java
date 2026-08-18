package com.portfolio.agent.turn.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.portfolio.agent.turn.lifecycle.AgentTurnLifecycleService;
import com.portfolio.agent.turn.projection.PublicAgentTurn;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PublicAgentTurnResponse {
    @JsonUnwrapped
    private final PublicAgentTurn turn;
    private final ConversationMetadata conversation;

    public PublicAgentTurnResponse(
            PublicAgentTurn turn,
            AgentTurnLifecycleService.ConversationMetadata conversation) {
        this.turn = Objects.requireNonNull(turn, "turn");
        this.conversation = conversation == null ? null : new ConversationMetadata(
                conversation.conversationId(), conversation.resumeToken());
    }
    @JsonUnwrapped public PublicAgentTurn getTurn() { return turn; }
    public ConversationMetadata getConversation() { return conversation; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConversationMetadata(String conversationId, String resumeToken) { }
}
