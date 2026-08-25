package com.portfolio.agent.turn.projection;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Objects;

/**
 * 单个 Goal 的公众投影结果：覆盖度、呈现与提示。
 *
 * <p>终态形状是强不变量——FULL 必须有呈现且无缺口提示；PARTIAL 必须有呈现且带
 * 缺口提示；NONE 必须无呈现且带提示（CONTINUATION_UNAVAILABLE 之外的提示都算
 * 缺口提示）。不可变，构造期校验。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class AnswerGoalResult {
    /** 不计入缺口提示的例外 code（续跑不可用属于提示而非覆盖缺口）。 */
    private static final String NON_GAP_NOTICE = "CONTINUATION_UNAVAILABLE";
    private final String goalId;
    private final String label;
    private final Coverage coverage;
    private final PublicPresentation presentation;
    private final List<GoalNotice> notices;

    public AnswerGoalResult(
            String goalId, String label, Coverage coverage,
            PublicPresentation presentation, List<GoalNotice> notices) {
        if (goalId == null || !goalId.matches("[a-z0-9][a-z0-9-]{1,95}")) {
            throw new IllegalArgumentException("goalId is invalid");
        }
        if (label == null || label.isBlank()) throw new IllegalArgumentException("label is required");
        this.goalId = goalId;
        this.label = label.trim();
        this.coverage = Objects.requireNonNull(coverage, "coverage");
        this.presentation = presentation;
        this.notices = List.copyOf(Objects.requireNonNull(notices, "notices"));
        boolean hasGapNotice = this.notices.stream().anyMatch(value -> !NON_GAP_NOTICE.equals(value.code()));
        if (coverage == Coverage.FULL && (presentation == null || hasGapNotice)) {
            throw new IllegalArgumentException("FULL goal invariant failed");
        }
        if (coverage == Coverage.PARTIAL && (presentation == null || !hasGapNotice)) {
            throw new IllegalArgumentException("PARTIAL goal invariant failed");
        }
        if (coverage == Coverage.NONE && (presentation != null || !hasGapNotice)) {
            throw new IllegalArgumentException("NONE goal invariant failed");
        }
    }
    public String getGoalId() { return goalId; }
    public String getLabel() { return label; }
    public Coverage getCoverage() { return coverage; }
    public PublicPresentation getPresentation() { return presentation; }
    public List<GoalNotice> getNotices() { return notices; }
    /** 目标覆盖度：完整 / 部分（带缺口提示）/ 无结果（仅提示）。 */
    public enum Coverage { FULL, PARTIAL, NONE }
}
