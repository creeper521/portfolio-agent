package com.portfolio.agent.turn.api.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * POST /api/agent/turns 的请求合同（Jackson 反序列化目标）。
 *
 * <p>字段级 Bean Validation + 未知字段拒绝（fail-closed：多出的字段直接抛错） +
 * 跨字段形状校验（窗口交替、CONTINUE 操作形状、referenceContextHandle 搭配），
 * 由 {@link AgentTurnRequestMapper} 翻译为闭合的 {@code AgentTurnCommand}。
 * toString 不输出任何访客文本，只保留标识符与计数。</p>
 */
public final class AgentTurnRequest {

    @NotNull(message = "requestId is required")
    private final UUID requestId;

    @Valid
    @NotNull(message = "modelSelection is required")
    private final ModelSelectionRequest modelSelection;

    @Valid
    @NotNull(message = "command is required")
    private final CommandRequest command;

    @Valid
    private final SurfaceContextRequest surfaceContext;

    @Valid
    @NotNull(message = "conversationWindow is required")
    @Size(max = 40, message = "conversationWindow must contain at most 40 messages")
    private final List<MessageRequest> conversationWindow;

    @JsonCreator
    public AgentTurnRequest(
            @JsonProperty("requestId") UUID requestId,
            @JsonProperty("modelSelection") ModelSelectionRequest modelSelection,
            @JsonProperty("command") CommandRequest command,
            @JsonProperty("surfaceContext") SurfaceContextRequest surfaceContext,
            @JsonProperty("conversationWindow") List<MessageRequest> conversationWindow) {
        this.requestId = requestId;
        this.modelSelection = modelSelection;
        this.command = command;
        this.surfaceContext = surfaceContext;
        this.conversationWindow = conversationWindow == null
                ? List.of()
                : List.copyOf(conversationWindow);
    }

    public UUID getRequestId() {
        return requestId;
    }

    public ModelSelectionRequest getModelSelection() {
        return modelSelection;
    }

    public CommandRequest getCommand() {
        return command;
    }

    public SurfaceContextRequest getSurfaceContext() {
        return surfaceContext;
    }

    public List<MessageRequest> getConversationWindow() {
        return conversationWindow;
    }

    /** fail-closed：任何未知字段都让反序列化立即失败，防止合同外输入被静默吞掉。 */
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("unknown request field: " + name);
    }

    /** 跨字段校验：会话窗口必须从 USER 开始严格 USER/ASSISTANT 交替。 */
    @AssertTrue(message = "conversationWindow must alternate USER and ASSISTANT")
    public boolean isConversationWindowOrderValid() {
        for (int index = 0; index < conversationWindow.size(); index++) {
            MessageRole expected = index % 2 == 0 ? MessageRole.USER : MessageRole.ASSISTANT;
            if (conversationWindow.get(index).getRole() != expected) {
                return false;
            }
        }
        return true;
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = AskCommandRequest.class, name = "ASK"),
            @JsonSubTypes.Type(value = ContinueCommandRequest.class, name = "CONTINUE"),
            @JsonSubTypes.Type(value = ResolveClarificationCommandRequest.class,
                    name = "RESOLVE_CLARIFICATION")
    })
    /** 命令多态标记：按 kind 判别为 ASK / CONTINUE / RESOLVE_CLARIFICATION。 */
    public interface CommandRequest {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = ModelModelSelectionRequest.class, name = "MODEL"),
            @JsonSubTypes.Type(value = NoneModelSelectionRequest.class, name = "NONE")
    })
    /** 模型选择多态标记：按 kind 判别为 MODEL / NONE。 */
    public interface ModelSelectionRequest {
    }

    /** 显式 MODEL 选择：modelRef 与 selectionVersion 均受格式与长度约束。 */
    public static final class ModelModelSelectionRequest implements ModelSelectionRequest {
        @NotBlank(message = "modelRef is required")
        @Size(max = 64, message = "modelRef must not exceed 64 characters")
        @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*",
                message = "modelRef format is invalid")
        private final String modelRef;

        @NotBlank(message = "selectionVersion is required")
        @Size(max = 128, message = "selectionVersion must not exceed 128 characters")
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*",
                message = "selectionVersion format is invalid")
        private final String selectionVersion;

        @JsonCreator
        public ModelModelSelectionRequest(
                @JsonProperty("modelRef") String modelRef,
                @JsonProperty("selectionVersion") String selectionVersion) {
            this.modelRef = modelRef;
            this.selectionVersion = selectionVersion;
        }

        public String getModelRef() {
            return modelRef;
        }

        public String getSelectionVersion() {
            return selectionVersion;
        }

        @JsonAnySetter
        public void rejectUnknownField(String name, Object value) {
            throw new IllegalArgumentException("unknown MODEL selection field: " + name);
        }
    }

    /** 显式 NONE 选择：不允许携带任何模型字段。 */
    public static final class NoneModelSelectionRequest implements ModelSelectionRequest {
        @JsonCreator
        public NoneModelSelectionRequest() {
        }

        @JsonAnySetter
        public void rejectUnknownField(String name, Object value) {
            throw new IllegalArgumentException("unknown NONE selection field: " + name);
        }
    }

    /** ASK 命令请求；referenceContextHandle 只能与 FREE_TEXT 输入搭配。 */
    public static final class AskCommandRequest implements CommandRequest {
        @Valid
        @NotNull(message = "ask input is required")
        private final AskInputRequest input;

        @Pattern(regexp = "[A-Za-z0-9_-]{8,256}",
                message = "referenceContextHandle format is invalid")
        private final String referenceContextHandle;

        @JsonCreator
        public AskCommandRequest(
                @JsonProperty("input") AskInputRequest input,
                @JsonProperty("referenceContextHandle")
                String referenceContextHandle) {
            this.input = input;
            this.referenceContextHandle = referenceContextHandle;
        }

        public AskInputRequest getInput() {
            return input;
        }

        public String getReferenceContextHandle() {
            return referenceContextHandle;
        }

        /** 跨字段校验：referenceContextHandle 只在 FREE_TEXT 输入时允许出现。 */
        @AssertTrue(message =
                "referenceContextHandle is allowed only for FREE_TEXT")
        public boolean isReferenceContextShapeValid() {
            return referenceContextHandle == null
                    || input instanceof FreeTextInputRequest;
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = FreeTextInputRequest.class, name = "FREE_TEXT"),
            @JsonSubTypes.Type(value = PresetInputRequest.class, name = "PRESET")
    })
    /** Ask 输入多态标记：按 kind 判别为 FREE_TEXT / PRESET。 */
    public interface AskInputRequest {
    }

    /** 自由文本输入（≤2000 字符）。 */
    public static final class FreeTextInputRequest implements AskInputRequest {
        @NotBlank(message = "text is required")
        @Size(max = 2000, message = "text must not exceed 2000 characters")
        private final String text;

        @JsonCreator
        public FreeTextInputRequest(@JsonProperty("text") String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }

    /** 预设输入：presetId + 固定格式的 presetRevision。 */
    public static final class PresetInputRequest implements AskInputRequest {
        @NotBlank(message = "presetId is required")
        @Pattern(regexp = "[a-z0-9-]{1,100}", message = "presetId format is invalid")
        private final String presetId;

        @NotBlank(message = "presetRevision is required")
        @Pattern(regexp = "pcv1-[a-f0-9]{16}", message = "presetRevision format is invalid")
        private final String presetRevision;

        @JsonCreator
        public PresetInputRequest(
                @JsonProperty("presetId") String presetId,
                @JsonProperty("presetRevision") String presetRevision) {
            this.presetId = presetId;
            this.presetRevision = presetRevision;
        }

        public String getPresetId() {
            return presetId;
        }

        public String getPresetRevision() {
            return presetRevision;
        }
    }

    /** CONTINUE 命令请求；字段组合必须匹配所选操作（见 {@link #isOperationShapeValid}）。 */
    public static final class ContinueCommandRequest implements CommandRequest {
        @NotNull(message = "continue operation is required")
        private final ContinueOperation operation;

        @Pattern(regexp = "[A-Za-z0-9_-]{8,256}", message = "contextHandle format is invalid")
        private final String contextHandle;

        @Pattern(regexp = "[A-Za-z0-9_-]{8,256}", message = "resultItemId format is invalid")
        private final String resultItemId;

        @Size(max = 2000, message = "text must not exceed 2000 characters")
        private final String text;

        @Valid
        private final ContinueSubjectRequest subject;

        @JsonCreator
        public ContinueCommandRequest(
                @JsonProperty("operation") ContinueOperation operation,
                @JsonProperty("contextHandle") String contextHandle,
                @JsonProperty("resultItemId") String resultItemId,
                @JsonProperty("text") String text,
                @JsonProperty("subject") ContinueSubjectRequest subject) {
            this.operation = operation;
            this.contextHandle = contextHandle;
            this.resultItemId = resultItemId;
            this.text = text;
            this.subject = subject;
        }

        public ContinueOperation getOperation() {
            return operation;
        }

        public String getContextHandle() {
            return contextHandle;
        }

        public String getResultItemId() {
            return resultItemId;
        }

        public String getText() {
            return text;
        }

        public ContinueSubjectRequest getSubject() {
            return subject;
        }

        /** 跨字段校验：每种 CONTINUE 操作允许携带的字段组合（与服务端命令形状一致）。 */
        @AssertTrue(message = "continue operation fields do not match")
        public boolean isOperationShapeValid() {
            if (operation == null) return false;
            return switch (operation) {
                case ENTER_RESULT -> contextHandle != null
                        && resultItemId != null && text == null && subject == null;
                case ROUTE_IN_CONTEXT -> contextHandle != null
                        && resultItemId == null
                        && text != null && !text.isBlank()
                        && subject == null;
                case EXIT_CONTEXT -> contextHandle != null
                        && resultItemId == null && text == null && subject == null;
                case REENTER_SUBJECT -> contextHandle == null
                        && resultItemId == null && text == null && subject != null;
            };
        }
    }

    /** REENTER_SUBJECT 的主体引用请求。 */
    public static final class ContinueSubjectRequest {
        @NotNull(message = "continue subject kind is required")
        private final ContinueSubjectKind kind;

        @NotBlank(message = "continue subject reference is required")
        @Pattern(regexp = "[A-Za-z0-9._-]{1,128}",
                message = "continue subject reference format is invalid")
        private final String reference;

        @JsonCreator
        public ContinueSubjectRequest(
                @JsonProperty("kind") ContinueSubjectKind kind,
                @JsonProperty("reference") String reference) {
            this.kind = kind;
            this.reference = reference;
        }

        public ContinueSubjectKind getKind() { return kind; }
        public String getReference() { return reference; }
    }

    /** RESOLVE_CLARIFICATION 命令请求：澄清 ID 与答案。 */
    public static final class ResolveClarificationCommandRequest implements CommandRequest {
        @NotBlank(message = "clarificationId is required")
        @Pattern(regexp = "[A-Za-z0-9_-]{8,256}", message = "clarificationId format is invalid")
        private final String clarificationId;

        @Valid
        @NotNull(message = "clarification answer is required")
        private final ClarificationAnswerRequest answer;

        @JsonCreator
        public ResolveClarificationCommandRequest(
                @JsonProperty("clarificationId") String clarificationId,
                @JsonProperty("answer") ClarificationAnswerRequest answer) {
            this.clarificationId = clarificationId;
            this.answer = answer;
        }

        public String getClarificationId() {
            return clarificationId;
        }

        public ClarificationAnswerRequest getAnswer() {
            return answer;
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = ChoiceAnswerRequest.class, name = "CHOICE"),
            @JsonSubTypes.Type(value = TextAnswerRequest.class, name = "TEXT")
    })
    /** 澄清答案多态标记：按 kind 判别为 CHOICE / TEXT。 */
    public interface ClarificationAnswerRequest {
    }

    /** 单选澄清答案。 */
    public static final class ChoiceAnswerRequest implements ClarificationAnswerRequest {
        @NotBlank(message = "choiceId is required")
        @Pattern(regexp = "[A-Za-z0-9_-]{8,256}", message = "choiceId format is invalid")
        private final String choiceId;

        @JsonCreator
        public ChoiceAnswerRequest(@JsonProperty("choiceId") String choiceId) {
            this.choiceId = choiceId;
        }

        public String getChoiceId() {
            return choiceId;
        }
    }

    /** 文本澄清答案（≤2000 字符）。 */
    public static final class TextAnswerRequest implements ClarificationAnswerRequest {
        @NotBlank(message = "text is required")
        @Size(max = 2000, message = "text must not exceed 2000 characters")
        private final String text;

        @JsonCreator
        public TextAnswerRequest(@JsonProperty("text") String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }

    /** 页面上下文请求（主体提示/受众/来源），整体可缺省。 */
    public static final class SurfaceContextRequest {
        @Valid
        private final SubjectHintRequest subjectHint;
        private final AudienceRole audienceRole;
        private final RequestSource requestSource;

        @JsonCreator
        public SurfaceContextRequest(
                @JsonProperty("subjectHint") SubjectHintRequest subjectHint,
                @JsonProperty("audienceRole") AudienceRole audienceRole,
                @JsonProperty("requestSource") RequestSource requestSource) {
            this.subjectHint = subjectHint;
            this.audienceRole = audienceRole;
            this.requestSource = requestSource;
        }

        public SubjectHintRequest getSubjectHint() {
            return subjectHint;
        }

        public AudienceRole getAudienceRole() {
            return audienceRole;
        }

        public RequestSource getRequestSource() {
            return requestSource;
        }
    }

    /** 页面主体提示请求：类别 + 公开 slug。 */
    public static final class SubjectHintRequest {
        @NotNull(message = "subject hint kind is required")
        private final SubjectHintKind kind;

        @NotBlank(message = "subject hint slug is required")
        @Pattern(regexp = "[a-z0-9-]{1,64}", message = "subject hint slug format is invalid")
        private final String slug;

        @JsonCreator
        public SubjectHintRequest(
                @JsonProperty("kind") SubjectHintKind kind,
                @JsonProperty("slug") String slug) {
            this.kind = kind;
            this.slug = slug;
        }

        public SubjectHintKind getKind() {
            return kind;
        }

        public String getSlug() {
            return slug;
        }
    }

    /** 单条会话窗口消息请求（角色 + 内容，内容 ≤4000 字符）。 */
    public static final class MessageRequest {
        @NotNull(message = "message role is required")
        private final MessageRole role;

        @NotBlank(message = "message content is required")
        @Size(max = 4000, message = "message content must not exceed 4000 characters")
        private final String content;

        @JsonCreator
        public MessageRequest(
                @JsonProperty("role") MessageRole role,
                @JsonProperty("content") String content) {
            this.role = role;
            this.content = content;
        }

        public MessageRole getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }
    }

    /** 会话消息角色。 */
    public enum MessageRole { USER, ASSISTANT }
    /** 主体提示类别。 */
    public enum SubjectHintKind { PROJECT, CASE }
    /** 续跑操作类别。 */
    public enum ContinueOperation {
        ENTER_RESULT, ROUTE_IN_CONTEXT, EXIT_CONTEXT, REENTER_SUBJECT
    }
    /** 续跑主体类别。 */
    public enum ContinueSubjectKind { PROJECT }
    /** 提问受众角色。 */
    public enum AudienceRole { INTERVIEWER, MENTOR, HR, GUEST }
    /** 请求来源页面。 */
    public enum RequestSource { HOME, PROJECT, CASE, AGENT_PAGE }

    @Override
    public String toString() {
        return "AgentTurnRequest{requestId=" + requestId
                + ", modelSelection=" + (modelSelection == null
                ? null : modelSelection.getClass().getSimpleName())
                + ", command=" + (command == null ? null : command.getClass().getSimpleName())
                + ", conversationMessageCount=" + conversationWindow.size() + '}';
    }
}
