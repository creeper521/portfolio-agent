package com.portfolio.agent.turn.projection;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.portfolio.agent.turn.continuation.ClarificationChallenge;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 面向公众的 Turn 终态投影：管线五种终态的 sealed 根类型。
 *
 * <p>Answer（结构化回答）、Clarification（澄清 challenge）、Conversational（会话式
 * 短句）、Boundary（边界拒绝）、CapabilityUnavailable（能力暂不可用）。同一 Turn 在
 * 生命周期内存在三份实例（只读返回、结算写入、持久化安全重放），但每份自身不可变。
 * 该类型同时是 HTTP 响应与 State 回放体的序列化合同（kind 判别）。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "kind", visible = false)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PublicAgentTurn.Answer.class, name = "ANSWER"),
        @JsonSubTypes.Type(value = PublicAgentTurn.Clarification.class, name = "CLARIFICATION"),
        @JsonSubTypes.Type(value = PublicAgentTurn.Conversational.class, name = "CONVERSATIONAL"),
        @JsonSubTypes.Type(value = PublicAgentTurn.Boundary.class, name = "BOUNDARY"),
        @JsonSubTypes.Type(value = PublicAgentTurn.CapabilityUnavailable.class,
                name = "CAPABILITY_UNAVAILABLE")
})
public abstract sealed class PublicAgentTurn permits
        PublicAgentTurn.Answer,
        PublicAgentTurn.Clarification,
        PublicAgentTurn.MessageTurn {
    private final UUID requestId;
    private final ModelExecutionProjection modelExecution;
    protected PublicAgentTurn(
            UUID requestId, ModelExecutionProjection modelExecution) {
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.modelExecution = Objects.requireNonNull(
                modelExecution, "modelExecution");
    }
    public UUID getRequestId() { return requestId; }
    public ModelExecutionProjection getModelExecution() { return modelExecution; }
    public abstract Kind getKind();

    /**
     * 返回携带指定模型执行投影的等价 Turn（逐子类型重建，自身不可变故需复制）。
     * 用于在结算前把 Claim 后冻结的执行快照投影统一附加到三份 Turn 上。
     */
    public final PublicAgentTurn withModelExecution(
            ModelExecutionProjection projection) {
        Objects.requireNonNull(projection, "projection");
        if (this instanceof Answer value) {
            return new Answer(getRequestId(), projection, value.getAnswer());
        }
        if (this instanceof Clarification value) {
            return new Clarification(
                    getRequestId(), projection, value.getMessage(),
                    value.getClarification(), value.getSuggestedActions());
        }
        if (this instanceof Conversational value) {
            return new Conversational(
                    getRequestId(), projection, value.getMessage(),
                    value.getSuggestedActions());
        }
        if (this instanceof Boundary value) {
            return new Boundary(
                    getRequestId(), projection, value.getCode(),
                    value.getMessage(), value.getSuggestedActions());
        }
        CapabilityUnavailable value = (CapabilityUnavailable) this;
        return new CapabilityUnavailable(
                getRequestId(), projection, value.getCode(), value.getMessage(),
                value.isRetryable(), value.getRetryAfterSeconds(),
                value.getSuggestedActions());
    }
    /** Turn 终态类别判别值（序列化为 kind 字段）。 */
    public enum Kind { ANSWER, CLARIFICATION, CONVERSATIONAL, BOUNDARY, CAPABILITY_UNAVAILABLE }

    /** 结构化回答终态：完整 PublicAnswer 载荷。 */
    public static final class Answer extends PublicAgentTurn {
        private final PublicAnswer answer;
        public Answer(UUID requestId, PublicAnswer answer) {
            this(requestId, ModelExecutionProjection.none(), answer);
        }
        @JsonCreator
        public Answer(
                @JsonProperty("requestId") UUID requestId,
                @JsonProperty("modelExecution") ModelExecutionProjection modelExecution,
                @JsonProperty("answer") PublicAnswer answer) {
            super(requestId, modelExecution);
            this.answer = Objects.requireNonNull(answer, "answer");
        }
        @Override public Kind getKind() { return Kind.ANSWER; }
        public PublicAnswer getAnswer() { return answer; }
    }

    /** 澄清终态：携带 challenge（关键澄清不得携带 affectedGoalIds）与建议动作。 */
    public static final class Clarification extends PublicAgentTurn {
        private final String message;
        private final ClarificationChallenge clarification;
        private final List<SuggestedAction> suggestedActions;
        public Clarification(
                UUID requestId, String message, ClarificationChallenge clarification,
                List<SuggestedAction> suggestedActions) {
            this(requestId, ModelExecutionProjection.none(), message,
                    clarification, suggestedActions);
        }
        @JsonCreator
        public Clarification(
                @JsonProperty("requestId") UUID requestId,
                @JsonProperty("modelExecution") ModelExecutionProjection modelExecution,
                @JsonProperty("message") String message,
                @JsonProperty("clarification") ClarificationChallenge clarification,
                @JsonProperty("suggestedActions") List<SuggestedAction> suggestedActions) {
            super(requestId, modelExecution); this.message = text(message, "message");
            this.clarification = Objects.requireNonNull(clarification, "clarification");
            if (!clarification.getAffectedGoalIds().isEmpty()) {
                throw new IllegalArgumentException("critical clarification cannot carry affectedGoalIds");
            }
            this.suggestedActions = List.copyOf(suggestedActions);
        }
        @Override public Kind getKind() { return Kind.CLARIFICATION; }
        public String getMessage() { return message; }
        public ClarificationChallenge getClarification() { return clarification; }
        public List<SuggestedAction> getSuggestedActions() { return suggestedActions; }
    }

    /** 会话式终态：单条固定短句 + 建议动作。 */
    public static final class Conversational extends MessageTurn {
        public Conversational(UUID requestId, String message, List<SuggestedAction> suggestedActions) {
            this(requestId, ModelExecutionProjection.none(), message, suggestedActions);
        }
        @JsonCreator
        public Conversational(
                @JsonProperty("requestId") UUID requestId,
                @JsonProperty("modelExecution") ModelExecutionProjection modelExecution,
                @JsonProperty("message") String message,
                @JsonProperty("suggestedActions") List<SuggestedAction> suggestedActions) {
            super(requestId, modelExecution, message, suggestedActions);
        }
        @Override public Kind getKind() { return Kind.CONVERSATIONAL; }
    }
    /** 边界终态：稳定 code 的越界/非法输入拒绝。 */
    public static final class Boundary extends CodedMessageTurn {
        public Boundary(UUID requestId, String code, String message, List<SuggestedAction> suggestedActions) {
            this(requestId, ModelExecutionProjection.none(), code, message, suggestedActions);
        }
        @JsonCreator
        public Boundary(
                @JsonProperty("requestId") UUID requestId,
                @JsonProperty("modelExecution") ModelExecutionProjection modelExecution,
                @JsonProperty("code") String code,
                @JsonProperty("message") String message,
                @JsonProperty("suggestedActions") List<SuggestedAction> suggestedActions) {
            super(requestId, modelExecution, code, message, suggestedActions);
        }
        @Override public Kind getKind() { return Kind.BOUNDARY; }
    }
    /** 能力不可用终态：稳定 code + 可重试语义（retryAfterSeconds 限定 1–300 秒）。 */
    public static final class CapabilityUnavailable extends CodedMessageTurn {
        private final boolean retryable;
        private final Long retryAfterSeconds;
        public CapabilityUnavailable(
                UUID requestId, String code, String message,
                boolean retryable, List<SuggestedAction> suggestedActions) {
            this(requestId, ModelExecutionProjection.none(), code, message,
                    retryable, null, suggestedActions);
        }
        public CapabilityUnavailable(
                UUID requestId, ModelExecutionProjection modelExecution,
                String code, String message,
                boolean retryable, List<SuggestedAction> suggestedActions) {
            this(requestId, modelExecution, code, message,
                    retryable, null, suggestedActions);
        }
        public CapabilityUnavailable(
                UUID requestId, String code, String message,
                boolean retryable, Long retryAfterSeconds,
                List<SuggestedAction> suggestedActions) {
            this(requestId, ModelExecutionProjection.none(), code, message,
                    retryable, retryAfterSeconds, suggestedActions);
        }
        @JsonCreator
        public CapabilityUnavailable(
                @JsonProperty("requestId") UUID requestId,
                @JsonProperty("modelExecution") ModelExecutionProjection modelExecution,
                @JsonProperty("code") String code,
                @JsonProperty("message") String message,
                @JsonProperty("retryable") boolean retryable,
                @JsonProperty("retryAfterSeconds") Long retryAfterSeconds,
                @JsonProperty("suggestedActions") List<SuggestedAction> suggestedActions) {
            super(requestId, modelExecution, code, message, suggestedActions);
            this.retryable = retryable;
            if (retryAfterSeconds != null
                    && (retryAfterSeconds < 1 || retryAfterSeconds > 300)) {
                throw new IllegalArgumentException(
                        "retryAfterSeconds is invalid");
            }
            this.retryAfterSeconds = retryAfterSeconds;
        }
        @Override public Kind getKind() { return Kind.CAPABILITY_UNAVAILABLE; }
        public boolean isRetryable() { return retryable; }
        public Long getRetryAfterSeconds() { return retryAfterSeconds; }
    }

    /** 带单条 message 与建议动作的中间密封层。 */
    public abstract static sealed class MessageTurn extends PublicAgentTurn
            permits Conversational, CodedMessageTurn {
        private final String message;
        private final List<SuggestedAction> suggestedActions;
        private MessageTurn(
                UUID requestId, ModelExecutionProjection modelExecution,
                String message, List<SuggestedAction> suggestedActions) {
            super(requestId, modelExecution); this.message = text(message, "message");
            this.suggestedActions = List.copyOf(
                    Objects.requireNonNull(suggestedActions, "suggestedActions"));
        }
        public String getMessage() { return message; }
        public List<SuggestedAction> getSuggestedActions() { return suggestedActions; }
    }
    /** 带稳定 code 的消息 Turn 密封层（Boundary / CapabilityUnavailable）。 */
    public abstract static sealed class CodedMessageTurn extends MessageTurn
            permits Boundary, CapabilityUnavailable {
        private final String code;
        private CodedMessageTurn(
                UUID requestId, ModelExecutionProjection modelExecution,
                String code, String message,
                List<SuggestedAction> suggestedActions) {
            super(requestId, modelExecution, message, suggestedActions);
            this.code = text(code, "code");
        }
        public String getCode() { return code; }
    }
    private static String text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
