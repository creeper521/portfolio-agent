package com.portfolio.agent.turn.lifecycle;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 闭合的 Agent Turn 命令：管线全部阶段的唯一输入载体。
 *
 * <p>三种具体命令——Ask（提问）、Continue（续跑操作）、ResolveClarification（回答澄清
 * challenge）。所有外部输入在构造期完成严格校验（不透明 Handle 的字符集、文本长度、
 * 枚举形状），后续阶段可以信任其形状。命令是隐私边界对象：toString 只输出 requestId、
 * 模型选择与消息计数，访客文本只进入指纹 HMAC 与模型输入，从不持久化。</p>
 */
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

    /**
     * 提问命令。referenceContextHandle 只允许与 FREE_TEXT 输入搭配，
     * 用于把自由文本路由到既有推荐上下文。
     */
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

    /** Ask 输入的多态标记接口。 */
    public interface AskInput {
    }

    /** 自由文本输入；文本必填且不超过 2000 字符。 */
    public static final class FreeText implements AskInput {
        private final String text;

        public FreeText(String text) {
            this.text = requireText(text, "text", 2000);
        }

        public String getText() {
            return text;
        }
    }

    /** 预设问题输入；presetRevision 形如 {@code pcv1-<hash>}，锁定预设内容版本。 */
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

    /**
     * 续跑命令。每种操作对携带字段有严格形状约束（见 {@link #operationShapeValid}），
     * contextHandle 等不透明 Handle 必须满足服务端 Handle 字符集。
     */
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

        /**
         * 操作形状状态机：ENTER_RESULT 要 handle+resultItemId，ROUTE_IN_CONTEXT 要
         * handle+text，EXIT_CONTEXT 只要 handle，REENTER_SUBJECT 只要 subject。
         * 违反组合的字段在构造期即拒绝。
         */
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

    /** 续跑主体引用：当前只支持 PROJECT 一种主体类别。 */
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

    /** 回答澄清 challenge 的命令；clarificationId 是服务端签发的不透明 Handle。 */
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

    /** 澄清答案的多态标记接口。 */
    public interface ClarificationAnswer {
    }

    /** 单选答案；choiceId 由 challenge 定义预先枚举。 */
    public static final class ChoiceAnswer implements ClarificationAnswer {
        private final String choiceId;

        public ChoiceAnswer(String choiceId) {
            this.choiceId = requireOpaque(choiceId, "choiceId");
        }

        public String getChoiceId() {
            return choiceId;
        }
    }

    /** 文本答案；受与自由提问相同的长度上界约束。 */
    public static final class TextAnswer implements ClarificationAnswer {
        private final String text;

        public TextAnswer(String text) {
            this.text = requireText(text, "text", 2000);
        }

        public String getText() {
            return text;
        }
    }

    /** 前端页面上下文提示（主体、受众、入口），全部可缺省。 */
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

        /** 无主体提示时的空上下文工厂。 */
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

    /** 页面主体提示：类别 + slug，slug 必须符合公开 slug 字符集。 */
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

    /** 主体提示类别。 */
    public enum SubjectHintKind {
        PROJECT,
        CASE
    }

    /** 续跑操作类别；决定 Continue 携带字段的合法组合。 */
    public enum ContinueOperation {
        /** 进入推荐结果详情并开启项目讨论。 */
        ENTER_RESULT,
        /** 在当前讨论上下文内追问。 */
        ROUTE_IN_CONTEXT,
        /** 退出当前讨论上下文。 */
        EXIT_CONTEXT,
        /** 重新进入一个此前讨论过的主体。 */
        REENTER_SUBJECT
    }

    /** 续跑主体类别。 */
    public enum ContinueSubjectKind {
        PROJECT
    }

    /** 提问受众角色，用于裁剪回答的呈现风格。 */
    public enum AudienceRole {
        INTERVIEWER,
        MENTOR,
        HR,
        GUEST
    }

    /** 请求来源页面，用于来源统计与提示消歧。 */
    public enum RequestSource {
        HOME,
        PROJECT,
        CASE,
        AGENT_PAGE
    }

    /** 模型选择类别：显式选择模型或显式无模型。 */
    public enum ModelSelectionKind {
        MODEL,
        NONE
    }

    /**
     * 模型选择：每个 Turn 必须显式 MODEL(modelRef + selectionVersion) 或 NONE，
     * 不允许隐式回退。MODEL 时两个字段都必须符合严格格式；NONE 时不得携带任何
     * 模型字段。不可变且按值相等。
     */
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

        /** 显式选择一个模型（modelRef + selectionVersion 快照）。 */
        public static ModelSelection model(String modelRef, String selectionVersion) {
            return new ModelSelection(
                    ModelSelectionKind.MODEL, modelRef, selectionVersion);
        }

        /** 显式无模型执行（纯确定性 Portfolio 路径）。 */
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

    /** 校验服务端签发的不透明 Handle 格式（字母数字下划线连字符，8–256 位）。 */
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
