package com.portfolio.agent.turn.projection;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.portfolio.agent.turn.continuation.ClarificationChallenge;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
    protected PublicAgentTurn(UUID requestId) {
        this.requestId = Objects.requireNonNull(requestId, "requestId");
    }
    public UUID getRequestId() { return requestId; }
    public abstract Kind getKind();
    public enum Kind { ANSWER, CLARIFICATION, CONVERSATIONAL, BOUNDARY, CAPABILITY_UNAVAILABLE }

    public static final class Answer extends PublicAgentTurn {
        private final PublicAnswer answer;
        public Answer(UUID requestId, PublicAnswer answer) {
            super(requestId); this.answer = Objects.requireNonNull(answer, "answer");
        }
        @Override public Kind getKind() { return Kind.ANSWER; }
        public PublicAnswer getAnswer() { return answer; }
    }

    public static final class Clarification extends PublicAgentTurn {
        private final String message;
        private final ClarificationChallenge clarification;
        private final List<SuggestedAction> suggestedActions;
        public Clarification(
                UUID requestId, String message, ClarificationChallenge clarification,
                List<SuggestedAction> suggestedActions) {
            super(requestId); this.message = text(message, "message");
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

    public static final class Conversational extends MessageTurn {
        public Conversational(UUID requestId, String message, List<SuggestedAction> suggestedActions) {
            super(requestId, message, suggestedActions);
        }
        @Override public Kind getKind() { return Kind.CONVERSATIONAL; }
    }
    public static final class Boundary extends CodedMessageTurn {
        public Boundary(UUID requestId, String code, String message, List<SuggestedAction> suggestedActions) {
            super(requestId, code, message, suggestedActions);
        }
        @Override public Kind getKind() { return Kind.BOUNDARY; }
    }
    public static final class CapabilityUnavailable extends CodedMessageTurn {
        private final boolean retryable;
        public CapabilityUnavailable(
                UUID requestId, String code, String message,
                boolean retryable, List<SuggestedAction> suggestedActions) {
            super(requestId, code, message, suggestedActions); this.retryable = retryable;
        }
        @Override public Kind getKind() { return Kind.CAPABILITY_UNAVAILABLE; }
        public boolean isRetryable() { return retryable; }
    }

    public abstract static sealed class MessageTurn extends PublicAgentTurn
            permits Conversational, CodedMessageTurn {
        private final String message;
        private final List<SuggestedAction> suggestedActions;
        private MessageTurn(UUID requestId, String message, List<SuggestedAction> suggestedActions) {
            super(requestId); this.message = text(message, "message");
            this.suggestedActions = List.copyOf(
                    Objects.requireNonNull(suggestedActions, "suggestedActions"));
        }
        public String getMessage() { return message; }
        public List<SuggestedAction> getSuggestedActions() { return suggestedActions; }
    }
    public abstract static sealed class CodedMessageTurn extends MessageTurn
            permits Boundary, CapabilityUnavailable {
        private final String code;
        private CodedMessageTurn(
                UUID requestId, String code, String message,
                List<SuggestedAction> suggestedActions) {
            super(requestId, message, suggestedActions); this.code = text(code, "code");
        }
        public String getCode() { return code; }
    }
    private static String text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
