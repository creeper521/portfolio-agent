package com.portfolio.agent.turn.planning;

import java.util.Objects;
import java.util.Optional;

/**
 * 目标解析结果：Goal 阶段的终态判定载体。
 *
 * <p>六种 {@link Kind} 互斥：正常产出目标提案、需要澄清、纯对话回复、
 * 能力边界、能力不可用、非法输入。携带消息的结果由 {@link MessageSource}
 * 区分服务端固定文案与 Provider 生成文案；Provider 文案不得原样持久化，
 * Settlement 前必须替换为固定终态。</p>
 */
public final class ResolvedGoalSet {
    private final Kind kind;
    private final UserGoalProposal goalProposal;
    private final ClarificationProposal clarification;
    private final String message;
    private final MessageSource messageSource;

    private ResolvedGoalSet(
            Kind kind,
            UserGoalProposal goalProposal,
            ClarificationProposal clarification,
            String message,
            MessageSource messageSource) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.goalProposal = goalProposal;
        this.clarification = clarification;
        this.message = message;
        this.messageSource = Objects.requireNonNull(messageSource, "messageSource");
    }

    public static ResolvedGoalSet goals(UserGoalProposal proposal) {
        return new ResolvedGoalSet(
                Kind.GOALS, Objects.requireNonNull(proposal, "proposal"), null, null,
                MessageSource.NONE);
    }

    public static ResolvedGoalSet clarification(ClarificationProposal proposal) {
        return new ResolvedGoalSet(Kind.CLARIFICATION, null,
                Objects.requireNonNull(proposal, "proposal"), null, MessageSource.NONE);
    }

    public static ResolvedGoalSet conversational(String message) {
        return message(Kind.CONVERSATIONAL, message, MessageSource.SERVER_FIXED);
    }

    public static ResolvedGoalSet providerConversational(String message) {
        return message(Kind.CONVERSATIONAL, message, MessageSource.PROVIDER_DERIVED);
    }

    public static ResolvedGoalSet boundary(String message) {
        return message(Kind.BOUNDARY, message, MessageSource.SERVER_FIXED);
    }

    public static ResolvedGoalSet capabilityUnavailable(String message) {
        return message(Kind.CAPABILITY_UNAVAILABLE, message, MessageSource.SERVER_FIXED);
    }

    public static ResolvedGoalSet invalidInput(String message) {
        return message(Kind.INVALID_INPUT, message, MessageSource.SERVER_FIXED);
    }

    /** 消息类结果的统一构造：文案必填、非空白且不超过 400 字符。 */
    private static ResolvedGoalSet message(
            Kind kind, String message, MessageSource messageSource) {
        if (message == null || message.isBlank() || message.length() > 400) {
            throw new IllegalArgumentException("resolved goal message is required and bounded");
        }
        return new ResolvedGoalSet(kind, null, null, message, messageSource);
    }

    public Kind getKind() { return kind; }
    public Optional<UserGoalProposal> getGoalProposal() { return Optional.ofNullable(goalProposal); }
    public Optional<ClarificationProposal> getClarification() {
        return Optional.ofNullable(clarification);
    }
    public Optional<String> getMessage() { return Optional.ofNullable(message); }
    public MessageSource getMessageSource() { return messageSource; }

    /** 终态类别：GOALS 产出目标；CLARIFICATION 需澄清；CONVERSATIONAL 纯对话；BOUNDARY 能力边界；CAPABILITY_UNAVAILABLE 能力不可用；INVALID_INPUT 输入非法。 */
    public enum Kind {
        /** 解析成功，携带目标提案。 */
        GOALS,
        /** 需要向访客澄清，携带澄清提案。 */
        CLARIFICATION,
        /** 纯社交/对话回复，仅携带消息。 */
        CONVERSATIONAL,
        /** 目标超出能力边界（高风险建议、实时信息等）。 */
        BOUNDARY,
        /** 当前无法可靠解析，服务端固定兜底文案。 */
        CAPABILITY_UNAVAILABLE,
        /** 输入本身非法（如主体提示指向不存在的公开主体）。 */
        INVALID_INPUT
    }

    /** 消息文案来源：NONE 无消息；SERVER_FIXED 服务端固定文案；PROVIDER_DERIVED 模型生成文案（不可持久化）。 */
    public enum MessageSource {
        NONE,
        SERVER_FIXED,
        PROVIDER_DERIVED
    }
}
