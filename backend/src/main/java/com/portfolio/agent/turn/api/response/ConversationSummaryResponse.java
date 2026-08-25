package com.portfolio.agent.turn.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portfolio.agent.turn.continuation.ActiveDiscussionPointer;
import com.portfolio.agent.turn.continuation.ContinuationReference;
import com.portfolio.agent.turn.lifecycle.AgentTurnLifecycleService;
import com.portfolio.agent.turn.projection.SuggestedAction;

import java.time.Instant;

/**
 * GET /api/agent/conversations/current 的响应合同：当前匿名会话摘要。
 *
 * <p>由生命周期层的 DiscussionSummary 投影而来，把讨论状态翻译成前端可直接渲染的
 * 建议动作（继续追问/结束讨论/重新进入/开始新话题）与续跑引用。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ConversationSummaryResponse {
    private final String conversationId;
    private final String status;
    private final long discussionRevision;
    private final ActiveDiscussion activeDiscussion;

    public ConversationSummaryResponse(
            String conversationId,
            long discussionRevision,
            AgentTurnLifecycleService.DiscussionSummary discussion) {
        this.conversationId = conversationId;
        this.status = "ACTIVE";
        this.discussionRevision = discussionRevision;
        this.activeDiscussion = discussion == null
                ? null : new ActiveDiscussion(discussion);
    }

    public String getConversationId() { return conversationId; }
    public String getStatus() { return status; }
    public long getDiscussionRevision() { return discussionRevision; }
    public ActiveDiscussion getActiveDiscussion() {
        return activeDiscussion;
    }

    /**
     * 活跃讨论投影：状态、主体、过期时间，以及按 ACTIVE/EXPIRED 二选一的动作集
     * （活跃时提供"结束讨论"，过期时提供"重新进入"与"开始新话题"）。
     */
    public static final class ActiveDiscussion {
        private final ActiveDiscussionPointer.Status status;
        private final Subject subject;
        private final Instant expiresAt;
        private final ContinuationReference routeContinuation;
        private final SuggestedAction exitAction;
        private final SuggestedAction reenterAction;
        private final SuggestedAction newTopicAction;

        ActiveDiscussion(
                AgentTurnLifecycleService.DiscussionSummary summary) {
            this.status = summary.status();
            this.subject = new Subject(
                    "PROJECT", summary.projectId(),
                    summary.label(), summary.route());
            this.expiresAt = summary.expiresAt();
            this.routeContinuation =
                    ContinuationReference.routeInContext(
                            summary.contextHandle());
            if (status == ActiveDiscussionPointer.Status.ACTIVE) {
                this.exitAction = new SuggestedAction(
                        "discussion-exit",
                        "结束讨论", null,
                        ContinuationReference.exitContext(
                                summary.contextHandle()));
                this.reenterAction = null;
                this.newTopicAction = null;
            } else {
                this.exitAction = null;
                this.reenterAction = new SuggestedAction(
                        "discussion-reenter",
                        "重新进入项目", null,
                        ContinuationReference.reenterSubject(
                                summary.projectId()));
                this.newTopicAction = new SuggestedAction(
                        "discussion-new-topic",
                        "开始新话题", null,
                        ContinuationReference.exitContext(
                                summary.contextHandle()));
            }
        }

        public ActiveDiscussionPointer.Status getStatus() { return status; }
        public Subject getSubject() { return subject; }
        public Instant getExpiresAt() { return expiresAt; }
        public ContinuationReference getRouteContinuation() {
            return routeContinuation;
        }
        public SuggestedAction getExitAction() { return exitAction; }
        public SuggestedAction getReenterAction() { return reenterAction; }
        public SuggestedAction getNewTopicAction() {
            return newTopicAction;
        }
    }

    /** 讨论主体：类别、引用 ID、展示名与站内路由。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static final class Subject {
        private final String kind;
        private final String reference;
        private final String label;
        private final String route;
        private Subject(
                String kind, String reference,
                String label, String route) {
            this.kind = kind;
            this.reference = reference;
            this.label = label;
            this.route = route;
        }
        public String getKind() { return kind; }
        public String getReference() { return reference; }
        public String getLabel() { return label; }
        public String getRoute() { return route; }
    }
}
