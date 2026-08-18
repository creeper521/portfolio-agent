package com.portfolio.agent.answer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Objects;

/** stp-v3's sole public UI state discriminant and its bounded non-answer payload. */
public final class AgentInteractionResponse {

    private final Kind kind;
    private final String message;
    private final List<String> actionIds;
    private final List<String> reasonCodes;

    public AgentInteractionResponse(
            Kind kind, String message, List<String> actionIds, List<String> reasonCodes) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.message = message == null || message.isBlank() ? null : message.trim();
        this.actionIds = actionIds == null || actionIds.isEmpty() ? null : List.copyOf(actionIds);
        this.reasonCodes = reasonCodes == null || reasonCodes.isEmpty() ? null : List.copyOf(reasonCodes);
        if (kind == Kind.ANSWER && (this.message != null || this.actionIds != null || this.reasonCodes != null)) {
            throw new IllegalArgumentException("answer interaction cannot carry non-answer fields");
        }
        if (kind == Kind.CONVERSATIONAL && this.message == null) {
            throw new IllegalArgumentException("conversational interaction requires a message");
        }
    }

    public Kind getKind() { return kind; }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getMessage() { return message; }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public List<String> getActionIds() { return actionIds; }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public List<String> getReasonCodes() { return reasonCodes; }

    public enum Kind {
        ANSWER, CONVERSATIONAL, CLARIFICATION, CONFIRMATION, BOUNDARY, CAPABILITY_UNAVAILABLE
    }
}
