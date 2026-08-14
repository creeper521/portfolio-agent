package com.portfolio.agent.answer.routing.domain;

import com.portfolio.agent.answer.routing.service.LegacySemanticContextAdapter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Action-aware routing input. Conversation messages are intentionally absent:
 * they may be passed to a later answer composer, but never become a subject
 * source for routing.
 */
public final class SemanticTurnInput {

    private static final String SUPPORTED_AGENT_TURN_CONTRACT = "stp-v1";
    private static final String CURRENT_AGENT_TURN_CONTRACT = "stp-v2";
    private static final String DEFAULT_TURN_ID = "turn-local";

    private final String turnId;
    private final Action action;
    private final String question;
    private final SemanticContext semanticContext;
    private final LegacySemanticContextAdapter.LegacyContext legacyContext;
    private final List<SubjectReference> explicitResultReferences;
    private final List<SubjectReference> explicitSubjectReferences;
    private final List<SubjectReference> pageSubjects;
    private final PlanConfirmation.Submission confirmationSubmission;
    private final InvalidatedPlanReference invalidatedPlanReference;
    private final PlanAdjustment planAdjustment;
    private final ClarificationResolution clarificationResolution;
    private final String requestToken;
    private final String agentTurnContract;
    private final String questionPresetId;
    private final String presetContractVersion;

    /**
     * Compatibility constructor for deterministic routing tests and callers
     * that already use the default ASK action.
     */
    public SemanticTurnInput(
            String question,
            SemanticContext semanticContext,
            LegacySemanticContextAdapter.LegacyContext legacyContext,
            List<SubjectReference> explicitResultReferences,
            List<SubjectReference> explicitSubjectReferences,
            List<SubjectReference> pageSubjects) {
        this(
                DEFAULT_TURN_ID,
                Action.ASK,
                question,
                semanticContext,
                legacyContext,
                explicitResultReferences,
                explicitSubjectReferences,
                pageSubjects,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public SemanticTurnInput(
            String turnId,
            Action action,
            String question,
            SemanticContext semanticContext,
            LegacySemanticContextAdapter.LegacyContext legacyContext,
            List<SubjectReference> explicitResultReferences,
            List<SubjectReference> explicitSubjectReferences,
            List<SubjectReference> pageSubjects,
            PlanConfirmation.Submission confirmationSubmission,
            InvalidatedPlanReference invalidatedPlanReference,
            String requestToken,
            String agentTurnContract,
            String questionPresetId,
            String presetContractVersion) {
        this(turnId, action, question, semanticContext, legacyContext,
                explicitResultReferences, explicitSubjectReferences, pageSubjects,
                confirmationSubmission, invalidatedPlanReference, null, null,
                requestToken, agentTurnContract, questionPresetId, presetContractVersion);
    }

    public SemanticTurnInput(
            String turnId,
            Action action,
            String question,
            SemanticContext semanticContext,
            LegacySemanticContextAdapter.LegacyContext legacyContext,
            List<SubjectReference> explicitResultReferences,
            List<SubjectReference> explicitSubjectReferences,
            List<SubjectReference> pageSubjects,
            PlanConfirmation.Submission confirmationSubmission,
            InvalidatedPlanReference invalidatedPlanReference,
            PlanAdjustment planAdjustment,
            ClarificationResolution clarificationResolution,
            String requestToken,
            String agentTurnContract,
            String questionPresetId,
            String presetContractVersion) {
        this.turnId = requireText(turnId, "turnId");
        this.action = Objects.requireNonNull(action, "action");
        this.question = normalizeQuestion(question, action);
        this.semanticContext = semanticContext;
        this.legacyContext = legacyContext;
        this.explicitResultReferences = copyReferences(
                explicitResultReferences, "explicitResultReferences");
        this.explicitSubjectReferences = copyReferences(
                explicitSubjectReferences, "explicitSubjectReferences");
        this.pageSubjects = copyReferences(pageSubjects, "pageSubjects");
        this.confirmationSubmission = confirmationSubmission;
        this.invalidatedPlanReference = invalidatedPlanReference;
        this.planAdjustment = planAdjustment;
        this.clarificationResolution = clarificationResolution;
        this.requestToken = normalizeText(requestToken);
        this.agentTurnContract = normalizeContract(agentTurnContract);
        this.questionPresetId = normalizeText(questionPresetId);
        this.presetContractVersion = normalizePresetContract(presetContractVersion);
        validateActionPayload();
    }

    public static SemanticTurnInput ask(String question) {
        return new SemanticTurnInput(
                DEFAULT_TURN_ID, Action.ASK, question, null, null,
                List.of(), List.of(), List.of(), null, null, null, null, null, null);
    }

    public static SemanticTurnInput confirmPlan(
            String turnId,
            PlanConfirmation.Submission submission) {
        return new SemanticTurnInput(
                turnId, Action.CONFIRM_PLAN, null, null, null,
                List.of(), List.of(), List.of(), submission, null, null,
                SUPPORTED_AGENT_TURN_CONTRACT, null, null);
    }

    public static SemanticTurnInput regeneratePlan(
            String turnId,
            String question,
            InvalidatedPlanReference invalidatedPlanReference,
            SemanticContext semanticContext) {
        return new SemanticTurnInput(
                turnId, Action.REGENERATE_PLAN, question, semanticContext, null,
                List.of(), List.of(), List.of(), null, invalidatedPlanReference,
                null, SUPPORTED_AGENT_TURN_CONTRACT, null, null);
    }

    public String getTurnId() {
        return turnId;
    }

    public Action getAction() {
        return action;
    }

    public String getQuestion() {
        return question;
    }

    public SemanticContext getSemanticContext() {
        return semanticContext;
    }

    public LegacySemanticContextAdapter.LegacyContext getLegacyContext() {
        return legacyContext;
    }

    public List<SubjectReference> getExplicitResultReferences() {
        return explicitResultReferences;
    }

    public List<SubjectReference> getExplicitSubjectReferences() {
        return explicitSubjectReferences;
    }

    public List<SubjectReference> getPageSubjects() {
        return pageSubjects;
    }

    public PlanConfirmation.Submission getConfirmationSubmission() {
        return confirmationSubmission;
    }

    /** Wire-contract alias retained for DTO mappers that call this field planConfirmation. */
    public PlanConfirmation.Submission getPlanConfirmation() {
        return confirmationSubmission;
    }

    public InvalidatedPlanReference getInvalidatedPlanReference() {
        return invalidatedPlanReference;
    }

    public PlanAdjustment getPlanAdjustment() {
        return planAdjustment;
    }

    public ClarificationResolution getClarificationResolution() {
        return clarificationResolution;
    }

    public String getRoutingQuestion() {
        if (clarificationResolution != null && clarificationResolution.isTextResolution()) {
            return clarificationResolution.getTextValue();
        }
        if (planAdjustment != null) {
            return question + "\n调整要求：" + planAdjustment.getInstruction();
        }
        return question;
    }

    public String getRequestToken() {
        return requestToken;
    }

    public String getAgentTurnContract() {
        return agentTurnContract;
    }

    public String getQuestionPresetId() {
        return questionPresetId;
    }

    public String getPresetContractVersion() {
        return presetContractVersion;
    }

    public SemanticTurnInput withExplicitSubjectReference(SubjectReference reference) {
        Objects.requireNonNull(reference, "reference");
        List<SubjectReference> subjects = new java.util.ArrayList<>(explicitSubjectReferences);
        if (!subjects.contains(reference)) subjects.add(reference);
        return new SemanticTurnInput(turnId, action, question, semanticContext, legacyContext,
                explicitResultReferences, subjects, pageSubjects, confirmationSubmission,
                invalidatedPlanReference, planAdjustment, clarificationResolution, requestToken,
                agentTurnContract, questionPresetId, presetContractVersion);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SemanticTurnInput that)) {
            return false;
        }
        return Objects.equals(turnId, that.turnId)
                && action == that.action
                && Objects.equals(question, that.question)
                && Objects.equals(semanticContext, that.semanticContext)
                && Objects.equals(legacyContext, that.legacyContext)
                && Objects.equals(explicitResultReferences, that.explicitResultReferences)
                && Objects.equals(explicitSubjectReferences, that.explicitSubjectReferences)
                && Objects.equals(pageSubjects, that.pageSubjects)
                && submissionEquals(confirmationSubmission, that.confirmationSubmission)
                && Objects.equals(invalidatedPlanReference, that.invalidatedPlanReference)
                && Objects.equals(planAdjustment, that.planAdjustment)
                && Objects.equals(clarificationResolution, that.clarificationResolution)
                && Objects.equals(requestToken, that.requestToken)
                && Objects.equals(agentTurnContract, that.agentTurnContract)
                && Objects.equals(questionPresetId, that.questionPresetId)
                && Objects.equals(presetContractVersion, that.presetContractVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                turnId, action, question, semanticContext, legacyContext,
                explicitResultReferences, explicitSubjectReferences, pageSubjects,
                submissionHash(confirmationSubmission), invalidatedPlanReference,
                planAdjustment, clarificationResolution,
                requestToken, agentTurnContract, questionPresetId, presetContractVersion);
    }

    @Override
    public String toString() {
        return "SemanticTurnInput{turnId=<redacted>, action=" + action
                + ", question=<redacted>, explicitResultReferenceCount="
                + explicitResultReferences.size()
                + ", explicitSubjectReferenceCount=" + explicitSubjectReferences.size()
                + ", pageSubjectCount=" + pageSubjects.size()
                + ", hasConfirmationSubmission=" + (confirmationSubmission != null)
                + ", hasInvalidatedPlanReference=" + (invalidatedPlanReference != null)
                + ", hasPlanAdjustment=" + (planAdjustment != null)
                + ", hasClarificationResolution=" + (clarificationResolution != null) + '}';
    }

    public enum Action {
        ASK,
        CONFIRM_PLAN,
        REGENERATE_PLAN
    }

    public static final class InvalidatedPlanReference {

        private final String planId;
        private final String planFingerprint;

        public InvalidatedPlanReference(String planId, String planFingerprint) {
            this.planId = requireText(planId, "planId");
            this.planFingerprint = requireText(planFingerprint, "planFingerprint");
        }

        public String getPlanId() {
            return planId;
        }

        public String getPlanFingerprint() {
            return planFingerprint;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof InvalidatedPlanReference that)) {
                return false;
            }
            return Objects.equals(planId, that.planId)
                    && Objects.equals(planFingerprint, that.planFingerprint);
        }

        @Override
        public int hashCode() {
            return Objects.hash(planId, planFingerprint);
        }

        @Override
        public String toString() {
            return "InvalidatedPlanReference{hasReference=true, hasFingerprint=true}";
        }
    }

    public static final class PendingPlanIdentity {

        private final String planId;
        private final String planFingerprint;

        public PendingPlanIdentity(String planId, String planFingerprint) {
            this.planId = requireText(planId, "planId");
            this.planFingerprint = requireText(planFingerprint, "planFingerprint");
        }

        public String getPlanId() { return planId; }
        public String getPlanFingerprint() { return planFingerprint; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof PendingPlanIdentity that)) return false;
            return Objects.equals(planId, that.planId)
                    && Objects.equals(planFingerprint, that.planFingerprint);
        }

        @Override
        public int hashCode() {
            return Objects.hash(planId, planFingerprint);
        }

        @Override
        public String toString() {
            return "PendingPlanIdentity{hasPlanId=true, hasFingerprint=true}";
        }
    }

    public static final class PlanAdjustment {

        private final String instruction;
        private final PendingPlanIdentity pendingPlanReference;

        public PlanAdjustment(String instruction, PendingPlanIdentity pendingPlanReference) {
            this.instruction = requireText(instruction, "instruction");
            if (this.instruction.length() > 500) {
                throw new IllegalArgumentException("instruction must not exceed 500 characters");
            }
            this.pendingPlanReference = Objects.requireNonNull(
                    pendingPlanReference, "pendingPlanReference");
        }

        public String getInstruction() { return instruction; }
        public PendingPlanIdentity getPendingPlanReference() { return pendingPlanReference; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof PlanAdjustment that)) return false;
            return Objects.equals(instruction, that.instruction)
                    && Objects.equals(pendingPlanReference, that.pendingPlanReference);
        }

        @Override
        public int hashCode() {
            return Objects.hash(instruction, pendingPlanReference);
        }

        @Override
        public String toString() {
            return "PlanAdjustment{instruction=<redacted>, hasPendingPlanReference=true}";
        }
    }

    public static final class ClarificationResolution {

        private final String clarificationId;
        private final String promptCode;
        private final String fieldKey;
        private final String selectedValue;
        private final SubjectReference selectedSubject;
        private final String textValue;

        public ClarificationResolution(
                String clarificationId,
                String promptCode,
                String fieldKey,
                String selectedValue,
                SubjectReference selectedSubject,
                String textValue) {
            this.clarificationId = requireText(clarificationId, "clarificationId");
            this.promptCode = requireText(promptCode, "promptCode");
            this.fieldKey = requireText(fieldKey, "fieldKey");
            this.selectedValue = normalizeText(selectedValue);
            this.selectedSubject = selectedSubject;
            this.textValue = normalizeText(textValue);
            validate();
        }

        public String getClarificationId() { return clarificationId; }
        public String getPromptCode() { return promptCode; }
        public String getFieldKey() { return fieldKey; }
        public String getSelectedValue() { return selectedValue; }
        public SubjectReference getSelectedSubject() { return selectedSubject; }
        public String getTextValue() { return textValue; }
        public boolean isTextResolution() { return textValue != null; }

        private void validate() {
            if (!clarificationId.matches("clarify-[a-f0-9]{32}")) {
                throw new IllegalArgumentException("clarificationId format is invalid");
            }
            if (!promptCode.matches("[A-Z]+_[A-Z0-9_]+")) {
                throw new IllegalArgumentException("promptCode format is invalid");
            }
            boolean selected = selectedValue != null || selectedSubject != null;
            if (selected == (textValue != null)) {
                throw new IllegalArgumentException("exactly one clarification value is required");
            }
            if ("taskSplit".equals(fieldKey)) {
                if (textValue == null || !"ROUTING_TASK_SPLIT_REQUIRED".equals(promptCode)) {
                    throw new IllegalArgumentException("taskSplit requires a matching text resolution");
                }
                if (textValue.length() > 2000) {
                    throw new IllegalArgumentException("textValue must not exceed 2000 characters");
                }
                return;
            }
            if (selectedValue == null || selectedSubject == null
                    || !selectedValue.equals(selectedSubject.getSubjectId())) {
                throw new IllegalArgumentException("selected option requires a matching subject reference");
            }
            if ("comparisonSubject".equals(fieldKey)) {
                if (!"ROUTING_COMPARISON_SUBJECT_MISSING".equals(promptCode)
                        || selectedSubject.getSubjectType()
                        != com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType.PROJECT) {
                    throw new IllegalArgumentException("comparisonSubject resolution is invalid");
                }
                return;
            }
            if ("subject".equals(fieldKey)) {
                if (!"ROUTING_SUBJECT_CLARIFICATION_REQUIRED".equals(promptCode)
                        || selectedSubject.getSubjectType()
                        == com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType.RESULT) {
                    throw new IllegalArgumentException("subject resolution is invalid");
                }
                return;
            }
            throw new IllegalArgumentException("clarification field is invalid");
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ClarificationResolution that)) return false;
            return Objects.equals(clarificationId, that.clarificationId)
                    && Objects.equals(promptCode, that.promptCode)
                    && Objects.equals(fieldKey, that.fieldKey)
                    && Objects.equals(selectedValue, that.selectedValue)
                    && Objects.equals(selectedSubject, that.selectedSubject)
                    && Objects.equals(textValue, that.textValue);
        }

        @Override
        public int hashCode() {
            return Objects.hash(clarificationId, promptCode, fieldKey,
                    selectedValue, selectedSubject, textValue);
        }

        @Override
        public String toString() {
            return "ClarificationResolution{promptCode=" + promptCode
                    + ", fieldKey=" + fieldKey + ", value=<redacted>}";
        }
    }

    private void validateActionPayload() {
        if (action == Action.CONFIRM_PLAN) {
            if (question != null || confirmationSubmission == null) {
                throw new IllegalArgumentException(
                        "CONFIRM_PLAN requires a signed submission and no question");
            }
            if (invalidatedPlanReference != null) {
                throw new IllegalArgumentException("CONFIRM_PLAN cannot carry an invalidated plan reference");
            }
            if (planAdjustment != null || clarificationResolution != null) {
                throw new IllegalArgumentException("CONFIRM_PLAN cannot carry ASK continuation values");
            }
            return;
        }
        if (confirmationSubmission != null) {
            throw new IllegalArgumentException(action + " cannot carry a confirmation submission");
        }
        if (action == Action.REGENERATE_PLAN && semanticContext == null) {
            throw new IllegalArgumentException(
                    "REGENERATE_PLAN requires current semanticContext");
        }
        if (action != Action.ASK && (planAdjustment != null || clarificationResolution != null)) {
            throw new IllegalArgumentException(action + " cannot carry ASK continuation values");
        }
        if (planAdjustment != null && clarificationResolution != null) {
            throw new IllegalArgumentException(
                    "plan adjustment and clarification resolution are mutually exclusive");
        }
        if ((planAdjustment != null || clarificationResolution != null) && semanticContext == null) {
            throw new IllegalArgumentException("ASK continuation requires semanticContext");
        }
        if (planAdjustment != null) {
            SemanticContext.PendingPlanReference contextReference = semanticContext
                    .getPendingPlanReference().orElseThrow(() -> new IllegalArgumentException(
                            "plan adjustment requires semanticContext.pendingPlanReference"));
            PendingPlanIdentity adjustmentReference = planAdjustment.getPendingPlanReference();
            if (!contextReference.getReferenceId().equals(adjustmentReference.getPlanId())
                    || !Objects.equals(contextReference.getPlanFingerprint(),
                            adjustmentReference.getPlanFingerprint())) {
                throw new IllegalArgumentException("pending plan references must match");
            }
        }
    }

    private static String normalizeQuestion(String value, Action action) {
        String normalized = normalizeText(value);
        if (action != Action.CONFIRM_PLAN && normalized == null) {
            throw new IllegalArgumentException(action + " requires a question");
        }
        if (normalized != null && normalized.length() > 2000) {
            throw new IllegalArgumentException("question must not exceed 2000 characters");
        }
        return normalized;
    }

    private static String normalizeContract(String value) {
        String normalized = normalizeText(value);
        if (normalized != null
                && !SUPPORTED_AGENT_TURN_CONTRACT.equals(normalized)
                && !CURRENT_AGENT_TURN_CONTRACT.equals(normalized)) {
            throw new IllegalArgumentException("agentTurnContract must be stp-v1 or stp-v2");
        }
        return normalized;
    }

    private static String normalizePresetContract(String value) {
        String normalized = normalizeText(value);
        if (normalized != null && !normalized.matches("pcv1-[a-f0-9]{16}")) {
            throw new IllegalArgumentException("presetContractVersion must be a pcv1 contract");
        }
        return normalized;
    }

    private static boolean submissionEquals(
            PlanConfirmation.Submission left,
            PlanConfirmation.Submission right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.getConfirmationId(), right.getConfirmationId())
                && Objects.equals(left.getConfirmationPlan(), right.getConfirmationPlan())
                && Objects.equals(left.getPlanFingerprint(), right.getPlanFingerprint())
                && Objects.equals(left.getIntegrityToken(), right.getIntegrityToken());
    }

    private static int submissionHash(PlanConfirmation.Submission submission) {
        if (submission == null) {
            return 0;
        }
        return Objects.hash(
                submission.getConfirmationId(), submission.getConfirmationPlan(),
                submission.getPlanFingerprint(), submission.getIntegrityToken());
    }

    private static List<SubjectReference> copyReferences(List<SubjectReference> values, String name) {
        List<SubjectReference> copied = List.copyOf(Objects.requireNonNull(values, name));
        if (new LinkedHashSet<>(copied).size() != copied.size()) {
            throw new IllegalArgumentException(name + " must not contain duplicates");
        }
        return copied;
    }

    private static String requireText(String value, String name) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return normalized;
    }

    private static String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
