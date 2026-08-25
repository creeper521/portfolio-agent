package com.portfolio.agent.turn.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.portfolio.agent.turn.lifecycle.AgentTurnLifecycleService;
import com.portfolio.agent.turn.projection.PublicAgentTurn;

import java.util.Objects;

/**
 * POST /api/agent/turns 成功时的响应合同：平铺的 PublicAgentTurn
 * （{@code @JsonUnwrapped} 展开各 Turn 子类型字段）+ 可选的一次性会话元数据。
 *
 * <p>会话元数据仅在本次请求提交或重放了会话时出现，resumeToken 是一次性签发的
 * 短时凭证，由前端存入当前标签页 sessionStorage。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PublicAgentTurnResponse {
    @JsonUnwrapped
    @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
    private final PublicAgentTurn turn;
    private final ConversationMetadata conversation;

    public PublicAgentTurnResponse(
            PublicAgentTurn turn,
            AgentTurnLifecycleService.ConversationMetadata conversation) {
        this.turn = Objects.requireNonNull(turn, "turn");
        this.conversation = conversation == null ? null : new ConversationMetadata(
                conversation.conversationId(), conversation.resumeToken(),
                conversation.discussionRevision(),
                conversation.discussion() == null ? null
                        : new ConversationSummaryResponse.ActiveDiscussion(
                        conversation.discussion()));
    }
    @JsonUnwrapped
    @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
    public PublicAgentTurn getTurn() { return turn; }
    public ConversationMetadata getConversation() { return conversation; }

    /** 会话元数据：会话 ID、一次性 ResumeToken、讨论修订号与活跃讨论投影。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConversationMetadata(
            String conversationId, String resumeToken,
            long discussionRevision,
            ConversationSummaryResponse.ActiveDiscussion activeDiscussion) { }
}
