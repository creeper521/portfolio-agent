package com.portfolio.agent.turn.lifecycle;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public abstract class AgentTurnCommand {

    private final UUID requestId;
    private final ModelSelection modelSelection;
    private final SurfaceContext surfaceContext;
    private final ConversationWindow conversationWindow;

    private AgentTurnCommand(
            UUID requestId,
            ModelSelection modelSelection,
            SurfaceContext surfaceContext,
            ConversationWindow conversationWindow) {
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.modelSelection = Objects.requireNonNull(modelSelection, "modelSelection");
        this.surfaceContext = surfaceContext == null ? SurfaceContext.empty() : surfaceContext;
        this.conversationWindow = conversationWindow == null
                ? ConversationWindow.empty()
                : conversationWindow;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public ModelSelection getModelSelection() {
        return modelSelection;
    }

    public SurfaceContext getSurfaceContext() {
        return surfaceContext;
    }

    public ConversationWindow getConversationWindow() {
        return conversationWindow;
    }

    public static final class Ask extends AgentTurnCommand {
        private final AskInput input;
        private final String referenceContextHandle;

        public Ask(
                UUID requestId,
                ModelSelection modelSelection,
                AskInput input,
                SurfaceContext surfaceContext,
                ConversationWindow conversationWindow) {
            this(requestId, modelSelection, input, null, surfaceContext, conversationWindow);
        }

        public Ask(
                UUID requestId,
                ModelSelection modelSelection,
                AskInput input,
                String referenceContextHandle,
                SurfaceContext surfaceContext,
                ConversationWindow conversationWindow) {
            super(requestId, modelSelection, surfaceContext, conversationWindow);
            this.input = Objects.requireNonNull(input, "input");
            this.referenceContextHandle = referenceContextHandle == null
                    ? null : requireOpaque(
                    referenceContextHandle, "referenceContextHandle");
            if (this.referenceContextHandle != null
                    && !(input instanceof FreeText)) {
                throw new IllegalArgumentException(
                        "referenceContextHandle requires FREE_TEXT");
            }
        }

        public AskInput getInput() {
            return input;
        }

        public Optional<String> getReferenceContextHandle() {
            return Optional.ofNullable(referenceContextHandle);
        }
    }

    public interface AskInput {
    }

    public static final class FreeText implements AskInput {
        private final String text;

        public FreeText(String text) {
            this.text = requireText(text, "text", 2000);
        }

        public String getText() {
            return text;
        }
    }

    public static final class Preset implements AskInput {
        private final String presetId;
        private final String presetRevision;

        public Preset(String presetId, String presetRevision) {
            this.presetId = requirePattern(
                    presetId, "presetId", "[a-z0-9-]{1,100}");
            this.presetRevision = requirePattern(
                    presetRevision, "presetRevision", "pcv1-[a-f0-9]{16}");
        }

        public String getPresetId() {
            return presetId;
        }

        public String getPresetRevision() {
            return presetRevision;
        }
    }

    public static final class Continue extends AgentTurnCommand {
        private final ContinueOperation operation;
        private final String contextHandle;
        private final String resultItemId;
        private final String text;
        private final ContinueSubject subject;

        public Continue(
                UUID requestId,
                ModelSelection modelSelection,
                ContinueOperation operation,
                String contextHandle,
                String resultItemId,
                String text,
                ContinueSubject subject,
                SurfaceContext surfaceContext,
                ConversationWindow conversationWindow) {
            super(requestId, modelSelection, surfaceContext, conversationWindow);
            this.operation = Objects.requireNonNull(operation, "operation");
            this.contextHandle = contextHandle == null
                    ? null : requireOpaque(contextHandle, "contextHandle");
            this.resultItemId = resultItemId == null
                    ? null
                    : requireOpaque(resultItemId, "resultItemId");
            this.text = text == null ? null : requireText(text, "text", 2000);
            this.subject = subject;
            if (!operationShapeValid()) {
                throw new IllegalArgumentException(
                        "continue operation fields do not match");
            }
        }

        public ContinueOperation getOperation() {
            return operation;
        }

        public Optional<String> getContextHandle() {
            return Optional.ofNullable(contextHandle);
        }

        public Optional<String> getResultItemId() {
            return Optional.ofNullable(resultItemId);
        }

        public Optional<String> getText() {
            return Optional.ofNullable(text);
        }

        public Optional<ContinueSubject> getSubject() {
            return Optional.ofNullable(subject);
        }

        private boolean operationShapeValid() {
            return switch (operation) {
                case ENTER_RESULT -> contextHandle != null
                        && resultItemId != null && text == null && subject == null;
                case ROUTE_IN_CONTEXT -> contextHandle != null
                        && resultItemId == null && text != null && subject == null;
                case EXIT_CONTEXT -> contextHandle != null
                        && resultItemId == null && text == null && subject == null;
                case REENTER_SUBJECT -> contextHandle == null
                        && resultItemId == null && text == null && subject != null;
            };
        }
    }

    public static final class ContinueSubject {
        private final ContinueSubjectKind kind;
        private final String reference;

        public ContinueSubject(
                ContinueSubjectKind kind, String reference) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.reference = requirePattern(
                    reference, "reference", "[A-Za-z0-9._-]{1,128}");
        }

        public ContinueSubjectKind getKind() { return kind; }
        public String getReference() { return reference; }
    }

    public static final class ResolveClarification extends AgentTurnCommand {
        private final String clarificationId;
        private final ClarificationAnswer answer;

        public ResolveClarification(
                UUID requestId,
                ModelSelection modelSelection,
                String clarificationId,
                ClarificationAnswer answer,
                SurfaceContext surfaceContext,
                ConversationWindow conversationWindow) {
            super(requestId, modelSelection, surfaceContext, conversationWindow);
            this.clarificationId = requireOpaque(clarificationId, "clarificationId");
            this.answer = Objects.requireNonNull(answer, "answer");
        }

        public String getClarificationId() {
            return clarificationId;
        }

        public ClarificationAnswer getAnswer() {
            return answer;
        }
    }

    public interface ClarificationAnswer {
    }

    public static final class ChoiceAnswer implements ClarificationAnswer {
        private final String choiceId;

        public ChoiceAnswer(String choiceId) {
            this.choiceId = requireOpaque(choiceId, "choiceId");
        }

        public String getChoiceId() {
            return choiceId;
        }
    }

    public static final class TextAnswer implements ClarificationAnswer {
        private final String text;

        public TextAnswer(String text) {
            this.text = requireText(text, "text", 2000);
        }

        public String getText() {
            return text;
        }
    }

    public static final class SurfaceContext {
        private final SubjectHint subjectHint;
        private final AudienceRole audienceRole;
        private final RequestSource requestSource;

        public SurfaceContext(
                SubjectHint subjectHint,
                AudienceRole audienceRole,
                RequestSource requestSource) {
            this.subjectHint = subjectHint;
            this.audienceRole = audienceRole;
            this.requestSource = requestSource;
        }

        public static SurfaceContext empty() {
            return new SurfaceContext(null, null, null);
        }

        public SubjectHint getSubjectHint() {
            return subjectHint;
        }

        public Optional<AudienceRole> getAudienceRole() {
            return Optional.ofNullable(audienceRole);
        }

        public Optional<RequestSource> getRequestSource() {
            return Optional.ofNullable(requestSource);
        }
    }

    public static final class SubjectHint {
        private final SubjectHintKind kind;
        private final String slug;

        public SubjectHint(SubjectHintKind kind, String slug) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.slug = requirePattern(slug, "slug", "[a-z0-9-]{1,64}");
        }

        public SubjectHintKind getKind() {
            return kind;
        }

        public String getSlug() {
            return slug;
        }
    }

    public enum SubjectHintKind {
        PROJECT,
        CASE
    }

    public enum ContinueOperation {
        ENTER_RESULT,
        ROUTE_IN_CONTEXT,
        EXIT_CONTEXT,
        REENTER_SUBJECT
    }

    public enum ContinueSubjectKind {
        PROJECT
    }

    public enum AudienceRole {
        INTERVIEWER,
        MENTOR,
        HR,
        GUEST
    }

    public enum RequestSource {
        HOME,
        PROJECT,
        CASE,
        AGENT_PAGE
    }

    public enum ModelSelectionKind {
        MODEL,
        NONE
    }

    public static final class ModelSelection {
        private final ModelSelectionKind kind;
        private final String modelRef;
        private final String selectionVersion;

        private ModelSelection(
                ModelSelectionKind kind,
                String modelRef,
                String selectionVersion) {
            this.kind = Objects.requireNonNull(kind, "kind");
            if (kind == ModelSelectionKind.MODEL) {
                this.modelRef = requireBoundedPattern(
                        modelRef, "modelRef", 64,
                        "[a-z0-9]+(?:-[a-z0-9]+)*");
                this.selectionVersion = requireBoundedPattern(
                        selectionVersion, "selectionVersion", 128,
                        "[A-Za-z0-9][A-Za-z0-9._-]*");
            } else {
                if (modelRef != null || selectionVersion != null) {
                    throw new IllegalArgumentException(
                            "NONE model selection must not carry model fields");
                }
                this.modelRef = null;
                this.selectionVersion = null;
            }
        }

        public static ModelSelection model(String modelRef, String selectionVersion) {
            return new ModelSelection(
                    ModelSelectionKind.MODEL, modelRef, selectionVersion);
        }

        public static ModelSelection none() {
            return new ModelSelection(ModelSelectionKind.NONE, null, null);
        }

        public ModelSelectionKind getKind() {
            return kind;
        }

        public Optional<String> getModelRef() {
            return Optional.ofNullable(modelRef);
        }

        public Optional<String> getSelectionVersion() {
            return Optional.ofNullable(selectionVersion);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ModelSelection that)) {
                return false;
            }
            return kind == that.kind
                    && Objects.equals(modelRef, that.modelRef)
                    && Objects.equals(selectionVersion, that.selectionVersion);
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind, modelRef, selectionVersion);
        }

        @Override
        public String toString() {
            return kind == ModelSelectionKind.NONE
                    ? "ModelSelection{kind=NONE}"
                    : "ModelSelection{kind=MODEL, modelRef=" + modelRef
                    + ", selectionVersion=" + selectionVersion + '}';
        }
    }

    private static String requireOpaque(String value, String name) {
        return requirePattern(value, name, "[A-Za-z0-9_-]{8,256}");
    }

    private static String requirePattern(String value, String name, String pattern) {
        if (value == null || !value.matches(pattern)) {
            throw new IllegalArgumentException(name + " format is invalid");
        }
        return value;
    }

    private static String requireBoundedPattern(
            String value, String name, int maximumLength, String pattern) {
        if (value == null || value.length() > maximumLength || !value.matches(pattern)) {
            throw new IllegalArgumentException(name + " format is invalid");
        }
        return value;
    }

    private static String requireText(String value, String name, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " is required and bounded");
        }
        return value;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{requestId=" + requestId
                + ", modelSelection=" + modelSelection
                + ", conversationMessageCount=" + conversationWindow.getMessages().size() + '}';
    }
}
