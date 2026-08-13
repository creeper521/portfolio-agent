package com.portfolio.agent.answer.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.portfolio.agent.answer.context.domain.CompletionReceipt;
import com.portfolio.agent.answer.context.domain.ConversationContinuationStatus;

import java.util.UUID;
import java.util.List;

/** Narrow public completion receipt; request fingerprint and internal conversation identity are excluded. */
public final class CompletionReceiptResponse {
    private final String responseKind = "COMPLETION_RECEIPT";
    private final String requestStatus = "REQUEST_ALREADY_COMPLETED";
    private final String turnId;
    private final UUID requestToken;
    private final String contextHandle;
    private final List<CompletedTask> completedTasks;
    private final ConversationContinuationStatus continuationStatus;
    private final ConversationResponse conversation;

    public CompletionReceiptResponse(String turnId, CompletionReceipt receipt) {
        this(turnId, receipt, null);
    }

    public CompletionReceiptResponse(
            String turnId, CompletionReceipt receipt, ConversationResponse conversation) {
        this.turnId = turnId;
        this.requestToken = receipt.getRequestToken();
        this.contextHandle = receipt.getContextHandle().map(value -> value.asBase64Url()).orElse(null);
        this.completedTasks = receipt.getContextHandle()
                .map(value -> List.of(new CompletedTask("01", "COMPLETED", value.asBase64Url())))
                .orElseGet(List::of);
        this.continuationStatus = receipt.getContinuationStatus();
        this.conversation = conversation;
    }

    public String getResponseKind() { return responseKind; }
    public String getRequestStatus() { return requestStatus; }
    public String getTurnId() { return turnId; }
    public UUID getRequestToken() { return requestToken; }
    @JsonIgnore
    public String getContextHandle() { return contextHandle; }
    public List<CompletedTask> getCompletedTasks() { return completedTasks; }
    public ConversationContinuationStatus getContinuationStatus() { return continuationStatus; }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public ConversationResponse getConversation() { return conversation; }

    public static final class CompletedTask {
        private final String displayIndex;
        private final String status;
        private final String contextHandle;

        public CompletedTask(String displayIndex, String status, String contextHandle) {
            this.displayIndex = displayIndex;
            this.status = status;
            this.contextHandle = contextHandle;
        }

        public String getDisplayIndex() { return displayIndex; }
        public String getStatus() { return status; }
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public String getContextHandle() { return contextHandle; }
    }
}
