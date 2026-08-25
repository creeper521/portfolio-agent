package com.portfolio.agent.turn.planning;

import java.util.Objects;

/**
 * 已验证语义计划：{@link SemanticPlanValidator} 通过后的包装类型。
 *
 * <p>只能在 planning 包内构造，作为"计划已满足全部不变量"的类型化凭证，
 * 供后续阶段以类型系统区分已验证与未验证计划。</p>
 */
public final class ValidatedSemanticTurnPlan {
    private final SemanticTurnPlan plan;

    ValidatedSemanticTurnPlan(SemanticTurnPlan plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
    }

    public SemanticTurnPlan getPlan() { return plan; }
}
