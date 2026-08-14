package com.portfolio.agent.answer.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.portfolio.agent.answer.context.domain.ConversationContextType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Top-level opaque Context reference; it does not carry Context payload or scope. */
public final class ContextReferenceRequest {
    @NotBlank(message = "contextHandle is required")
    @Size(max = 64, message = "contextHandle must not exceed 64 characters")
    @Pattern(regexp = "[A-Za-z0-9_-]{32}", message = "contextHandle format is invalid")
    private final String contextHandle;

    @NotNull(message = "expectedContextType is required")
    private final ConversationContextType expectedContextType;

    @Size(max = 100, message = "resultItemId must not exceed 100 characters")
    private final String resultItemId;

    @JsonCreator
    public ContextReferenceRequest(
            @JsonProperty("contextHandle") String contextHandle,
            @JsonProperty("expectedContextType") ConversationContextType expectedContextType,
            @JsonProperty("resultItemId") String resultItemId) {
        this.contextHandle = contextHandle;
        this.expectedContextType = expectedContextType;
        this.resultItemId = resultItemId;
    }

    public ContextReferenceRequest(String contextHandle, ConversationContextType expectedContextType) {
        this(contextHandle, expectedContextType, null);
    }

    public String getContextHandle() { return contextHandle; }
    public ConversationContextType getExpectedContextType() { return expectedContextType; }
    public String getResultItemId() { return resultItemId; }
}
