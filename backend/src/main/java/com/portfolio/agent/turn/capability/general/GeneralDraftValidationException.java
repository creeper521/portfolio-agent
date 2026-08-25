package com.portfolio.agent.turn.capability.general;

/**
 * Closed semantic rejection reason; provider output and exception text stay excluded.
 *
 * <p>通用草稿语义校验失败异常：Reason 是封闭词汇，是唯一对外暴露的失败类别；
 * Provider 原始输出与内部异常文本不进入该异常，防止不可信内容外泄。
 */
public final class GeneralDraftValidationException extends IllegalArgumentException {
    private final Reason reason;

    public GeneralDraftValidationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    /** 封闭拒绝原因：主题不符、EXPLANATION 角色/Aspect/覆盖无效、COMPARISON 角色/
     * aspects/配对重复/覆盖无效、caveat 重复、句界无效、语言或句数无效。 */
    public enum Reason {
        TOPIC_MISMATCH,
        EXPLANATION_ROLES_INVALID,
        EXPLANATION_ROLE_ASPECTS_INVALID,
        EXPLANATION_COVERAGE_INVALID,
        COMPARISON_ROLE_INVALID,
        COMPARISON_ASPECTS_INVALID,
        COMPARISON_DUPLICATE_PAIR,
        COMPARISON_COVERAGE_INVALID,
        CAVEAT_DUPLICATE,
        SENTENCE_BOUNDARY_INVALID,
        LANGUAGE_OR_SENTENCE_COUNT_INVALID
    }
}
