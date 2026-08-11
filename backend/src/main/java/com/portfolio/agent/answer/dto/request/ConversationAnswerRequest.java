package com.portfolio.agent.answer.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.portfolio.agent.answer.domain.ConversationMessageRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public final class ConversationAnswerRequest {

    @NotBlank(message = "turnId is required")
    @Size(max = 100, message = "turnId must not exceed 100 characters")
    private final String turnId;

    @NotNull(message = "requestToken is required")
    private final UUID requestToken;

    @Pattern(regexp = "[a-z0-9-]{1,100}", message = "questionPresetId format is invalid")
    private final String questionPresetId;

    @Pattern(regexp = "pcv1-[a-f0-9]{16}", message = "contractVersion format is invalid")
    private final String contractVersion;

    @Size(max = 2000, message = "question must not exceed 2000 characters")
    private final String question;

    @Pattern(regexp = "stp-v1", message = "agentTurnContract must be stp-v1 when present")
    private final String agentTurnContract;

    private final TurnAction action;

    @Valid
    private final SemanticContextRequest semanticContext;

    @Valid
    private final PlanConfirmationRequest planConfirmation;

    @Valid
    private final InvalidatedPlanReferenceRequest invalidatedPlanReference;

    @Valid
    private final PlanAdjustmentRequest planAdjustment;

    @Valid
    private final ClarificationResolutionRequest clarificationResolution;

    @Valid
    @NotNull(message = "messages are required")
    @Size(max = 40, message = "messages must contain at most 20 rounds")
    private final List<ConversationMessageRequest> messages;

    @Valid
    private final ConversationAnswerContextRequest context;

    @JsonCreator
    public ConversationAnswerRequest(
            @JsonProperty("turnId") String turnId,
            @JsonProperty("requestToken") UUID requestToken,
            @JsonProperty("questionPresetId") String questionPresetId,
            @JsonProperty("contractVersion") String contractVersion,
            @JsonProperty("action") TurnAction action,
            @JsonProperty("question") String question,
            @JsonProperty("messages") List<ConversationMessageRequest> messages,
            @JsonProperty("context") ConversationAnswerContextRequest context,
            @JsonProperty("semanticContext") SemanticContextRequest semanticContext,
            @JsonProperty("planConfirmation") PlanConfirmationRequest planConfirmation,
            @JsonProperty("invalidatedPlanReference") InvalidatedPlanReferenceRequest invalidatedPlanReference,
            @JsonProperty("planAdjustment") PlanAdjustmentRequest planAdjustment,
            @JsonProperty("clarificationResolution")
            ClarificationResolutionRequest clarificationResolution,
            @JsonProperty("agentTurnContract") String agentTurnContract
    ) {
        this.turnId = turnId;
        this.requestToken = requestToken;
        this.questionPresetId = questionPresetId;
        this.contractVersion = contractVersion;
        this.action = action == null ? TurnAction.ASK : action;
        this.question = question;
        this.messages = messages == null ? List.of() : List.copyOf(messages);
        this.context = context;
        this.semanticContext = semanticContext;
        this.planConfirmation = planConfirmation;
        this.invalidatedPlanReference = invalidatedPlanReference;
        this.planAdjustment = planAdjustment;
        this.clarificationResolution = clarificationResolution;
        this.agentTurnContract = agentTurnContract;
    }

    public ConversationAnswerRequest(
            String turnId,
            UUID requestToken,
            String questionPresetId,
            String contractVersion,
            TurnAction action,
            String question,
            List<ConversationMessageRequest> messages,
            ConversationAnswerContextRequest context,
            SemanticContextRequest semanticContext,
            PlanConfirmationRequest planConfirmation,
            InvalidatedPlanReferenceRequest invalidatedPlanReference,
            String agentTurnContract) {
        this(turnId, requestToken, questionPresetId, contractVersion, action, question, messages,
                context, semanticContext, planConfirmation, invalidatedPlanReference,
                null, null, agentTurnContract);
    }

    public ConversationAnswerRequest(
            String turnId,
            UUID requestToken,
            String question,
            List<ConversationMessageRequest> messages,
            ConversationAnswerContextRequest context
    ) {
        this(turnId, requestToken, null, null, TurnAction.ASK, question, messages, context,
                null, null, null, null, null, null);
    }

    public ConversationAnswerRequest(
            String turnId,
            String question,
            List<ConversationMessageRequest> messages,
            ConversationAnswerContextRequest context
    ) {
        this(turnId, UUID.nameUUIDFromBytes(
                String.valueOf(turnId).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                null, null, TurnAction.ASK, question, messages, context,
                null, null, null, null, null, null);
    }

    public String getTurnId() { return turnId; }
    public UUID getRequestToken() { return requestToken; }
    public String getQuestionPresetId() { return questionPresetId; }
    public String getContractVersion() { return contractVersion; }
    public TurnAction getAction() { return action; }
    public String getQuestion() { return question; }
    public List<ConversationMessageRequest> getMessages() { return messages; }
    public ConversationAnswerContextRequest getContext() { return context; }
    public SemanticContextRequest getSemanticContext() { return semanticContext; }
    public PlanConfirmationRequest getPlanConfirmation() { return planConfirmation; }
    public InvalidatedPlanReferenceRequest getInvalidatedPlanReference() {
        return invalidatedPlanReference;
    }
    public PlanAdjustmentRequest getPlanAdjustment() { return planAdjustment; }
    public ClarificationResolutionRequest getClarificationResolution() {
        return clarificationResolution;
    }
    public String getAgentTurnContract() { return agentTurnContract; }

    @AssertTrue(message = "question or plan confirmation is invalid for action")
    public boolean isActionPayloadValid() {
        return switch (action) {
            case ASK -> hasText(question) && planConfirmation == null && invalidatedPlanReference == null
                    && !(planAdjustment != null && clarificationResolution != null)
                    && ((planAdjustment == null && clarificationResolution == null)
                    || semanticContext != null);
            case REGENERATE_PLAN -> hasText(question) && planConfirmation == null
                    && semanticContext != null && invalidatedPlanReference != null
                    && planAdjustment == null && clarificationResolution == null;
            case CONFIRM_PLAN -> !hasText(question) && planConfirmation != null
                    && invalidatedPlanReference == null
                    && planAdjustment == null && clarificationResolution == null;
        };
    }

    @AssertTrue(message = "messages must alternate USER and ASSISTANT")
    public boolean isMessageOrderValid() {
        for (int index = 0; index < messages.size(); index++) {
            ConversationMessageRole expected = index % 2 == 0
                    ? ConversationMessageRole.USER
                    : ConversationMessageRole.ASSISTANT;
            if (messages.get(index).getRole() != expected) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public enum TurnAction {
        ASK,
        CONFIRM_PLAN,
        REGENERATE_PLAN
    }

    @Override
    public String toString() {
        return "ConversationAnswerRequest{" +
                "turnId='" + turnId + '\'' +
                ", question='<redacted>'" +
                ", messageCount=" + messages.size() +
                ", hasPlanAdjustment=" + (planAdjustment != null) +
                ", hasClarificationResolution=" + (clarificationResolution != null) +
                '}';
    }
}
