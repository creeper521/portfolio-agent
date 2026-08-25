package com.portfolio.agent.turn.continuation;

import java.util.Objects;

/**
 * Terminal-only clarification consumption owned by the claimed request.
 *
 * <p>澄清结算变更：Turn 结算时一次性消费澄清的原子载荷。只有 claim 了
 * 该请求的一方能在 Settlement 阶段消费澄清；NONE 单例表示本轮不消费。</p>
 */
public final class ClarificationSettlementMutation {
    private static final ClarificationSettlementMutation NONE =
            new ClarificationSettlementMutation(null, null);

    private final String clarificationId;
    private final ClarificationStore.ClarificationAnswer answer;

    private ClarificationSettlementMutation(
            String clarificationId,
            ClarificationStore.ClarificationAnswer answer) {
        this.clarificationId = clarificationId;
        this.answer = answer;
    }

    /** 空变更单例。 */
    public static ClarificationSettlementMutation none() {
        return NONE;
    }

    /** 构造消费指定澄清的变更。 */
    public static ClarificationSettlementMutation consume(
            String clarificationId,
            ClarificationStore.ClarificationAnswer answer) {
        return new ClarificationSettlementMutation(
                ContinuationContext.text(
                        clarificationId, "clarificationId"),
                Objects.requireNonNull(answer, "answer"));
    }

    /** 是否为空变更。 */
    public boolean isNone() {
        return clarificationId == null;
    }

    /** 澄清 ID；空变更时抛出 IllegalStateException。 */
    public String clarificationId() {
        if (isNone()) throw new IllegalStateException("clarification mutation is empty");
        return clarificationId;
    }

    /** 澄清答案；空变更时抛出 IllegalStateException。 */
    public ClarificationStore.ClarificationAnswer answer() {
        if (isNone()) throw new IllegalStateException("clarification mutation is empty");
        return answer;
    }
}
