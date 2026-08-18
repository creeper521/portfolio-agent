package com.portfolio.agent.turn.lifecycle;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public abstract class AgentTurnCommand {

    private final UUID requestId;
    private final SurfaceContext surfaceContext;
    private final ConversationWindow conversationWindow;

    private AgentTurnCommand(
            UUID requestId,
            SurfaceContext surfaceContext,
            ConversationWindow conversationWindow) {
        this.requestId = Objects.requireNonNull(requestId, "requestId");
        this.surfaceContext = surfaceContext == null ? SurfaceContext.empty() : surfaceContext;
        this.conversationWindow = conversationWindow == null
                ? ConversationWindow.empty()
                : conversationWindow;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public SurfaceContext getSurfaceContext() {
        return surfaceContext;
    }

    public ConversationWindow getConversationWindow() {
        return conversationWindow;
    }

    public static final class Ask extends AgentTurnCommand {
        private final AskInput input;

        public Ask(
                UUID requestId,
                AskInput input,
                SurfaceContext surfaceContext,
                ConversationWindow conversationWindow) {
            super(requestId, surfaceContext, conversationWindow);
            this.input = Objects.requireNonNull(input, "input");
        }

        public AskInput getInput() {
            return input;
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
        private final String contextHandle;
        private final String resultItemId;
        private final String text;

        public Continue(
                UUID requestId,
                String contextHandle,
                String resultItemId,
                String text,
                SurfaceContext surfaceContext,
                ConversationWindow conversationWindow) {
            super(requestId, surfaceContext, conversationWindow);
            this.contextHandle = requireOpaque(contextHandle, "contextHandle");
            this.resultItemId = resultItemId == null
                    ? null
                    : requireOpaque(resultItemId, "resultItemId");
            this.text = requireText(text, "text", 2000);
        }

        public String getContextHandle() {
            return contextHandle;
        }

        public Optional<String> getResultItemId() {
            return Optional.ofNullable(resultItemId);
        }

        public String getText() {
            return text;
        }
    }

    public static final class ResolveClarification extends AgentTurnCommand {
        private final String clarificationId;
        private final ClarificationAnswer answer;

        public ResolveClarification(
                UUID requestId,
                String clarificationId,
                ClarificationAnswer answer,
                SurfaceContext surfaceContext,
                ConversationWindow conversationWindow) {
            super(requestId, surfaceContext, conversationWindow);
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

    private static String requireOpaque(String value, String name) {
        return requirePattern(value, name, "[A-Za-z0-9_-]{8,256}");
    }

    private static String requirePattern(String value, String name, String pattern) {
        if (value == null || !value.matches(pattern)) {
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
                + ", conversationMessageCount=" + conversationWindow.getMessages().size() + '}';
    }
}
