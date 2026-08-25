package com.portfolio.agent.turn.lifecycle;

import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.SemanticTurnPlan;
import com.portfolio.agent.turn.projection.PublicAgentTurn;

import java.util.List;
import java.util.Objects;

/**
 * 持久化安全回放策略：判定重放时可以保留哪一份回答体。
 *
 * <p>确定性 Portfolio 结果可以按原样重放；Provider 生成的自由文本（Conversational、
 * 通用知识等）不允许进入持久化回放体，一律替换为固定终端
 * {@code REPLAY_BODY_NOT_RETAINED}。该判定发生在结算写入之前，是 State 边界的
 * 最后一道内容闸门。</p>
 */
public final class PersistenceSafeReplayPolicy {
    public static final String BODY_NOT_RETAINED_CODE = "REPLAY_BODY_NOT_RETAINED";
    public static final String BODY_NOT_RETAINED_MESSAGE = "该回答未被保留，请重新提问。";

    /**
     * Provider 派生回答体的重放投影：不保留任何原文，直接替换为固定终端 Turn。
     *
     * @param liveTurn 本次实时执行的 Turn（只取 requestId）
     */
    public PublicAgentTurn forProviderBody(PublicAgentTurn liveTurn) {
        Objects.requireNonNull(liveTurn, "liveTurn");
        return bodyNotRetained(liveTurn.getRequestId());
    }

    /**
     * 按计划的任务构成判定回放体：仅当计划非空且全部为确定性 Portfolio 任务
     * （FACT/COMPARE/RECOMMEND）时保留实时 Turn，否则替换为固定终端。
     *
     * @return 可安全持久化的重放投影
     */
    public PublicAgentTurn forPlan(
            PublicAgentTurn liveTurn, SemanticTurnPlan plan) {
        Objects.requireNonNull(liveTurn, "liveTurn");
        Objects.requireNonNull(plan, "plan");
        boolean portfolioOnly = !plan.getTasks().isEmpty()
                && plan.getTasks().stream()
                .map(SemanticTask::getType)
                .allMatch(List.of(
                        SemanticTask.Type.PORTFOLIO_FACT,
                        SemanticTask.Type.PORTFOLIO_COMPARE,
                        SemanticTask.Type.PORTFOLIO_RECOMMEND)::contains);
        return portfolioOnly ? liveTurn : bodyNotRetained(liveTurn.getRequestId());
    }

    /** 构造固定终端 CapabilityUnavailable Turn，消息为不可保留提示。 */
    private PublicAgentTurn bodyNotRetained(java.util.UUID requestId) {
        return new PublicAgentTurn.CapabilityUnavailable(
                requestId, BODY_NOT_RETAINED_CODE,
                BODY_NOT_RETAINED_MESSAGE, false, List.of());
    }
}
