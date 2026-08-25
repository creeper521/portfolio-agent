package com.portfolio.agent.turn.planning;

import java.util.Objects;
import java.util.Optional;

/**
 * 计划编译结果：Plan 阶段的终态判定载体。
 *
 * <p>COMPILED 携带通过 {@link SemanticPlanValidator} 校验的计划；
 * CLARIFICATION_REQUIRED 与 REJECTED 只携带闭合原因码，不携带细节文本。</p>
 */
public final class PlanCompilationResult {
    private final Kind kind;
    private final ValidatedSemanticTurnPlan plan;
    private final String reason;

    private PlanCompilationResult(Kind kind, ValidatedSemanticTurnPlan plan, String reason) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.plan = plan;
        this.reason = reason;
    }

    public static PlanCompilationResult compiled(ValidatedSemanticTurnPlan plan) {
        return new PlanCompilationResult(Kind.COMPILED, Objects.requireNonNull(plan, "plan"), null);
    }

    public static PlanCompilationResult clarificationRequired(String reason) {
        return new PlanCompilationResult(Kind.CLARIFICATION_REQUIRED, null, reason);
    }

    public static PlanCompilationResult rejected(String reason) {
        return new PlanCompilationResult(Kind.REJECTED, null, reason);
    }

    public Kind getKind() { return kind; }
    public Optional<ValidatedSemanticTurnPlan> getPlan() { return Optional.ofNullable(plan); }
    public Optional<String> getReason() { return Optional.ofNullable(reason); }

    /** 终态类别：COMPILED 编译成功；CLARIFICATION_REQUIRED 需要澄清；REJECTED 编译被拒绝。 */
    public enum Kind { COMPILED, CLARIFICATION_REQUIRED, REJECTED }
}
