package com.portfolio.agent.turn.projection;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portfolio.agent.turn.continuation.ContinuationReference;

import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class AnswerGoalResult {
    private static final String NON_GAP_NOTICE = "CONTINUATION_UNAVAILABLE";
    private final String goalId;
    private final String label;
    private final Coverage coverage;
    private final PublicPresentation presentation;
    private final List<GoalNotice> notices;
    private final ContinuationReference continuation;

    public AnswerGoalResult(
            String goalId, String label, Coverage coverage,
            PublicPresentation presentation, List<GoalNotice> notices,
            ContinuationReference continuation) {
        if (goalId == null || !goalId.matches("[a-z0-9][a-z0-9-]{1,95}")) {
            throw new IllegalArgumentException("goalId is invalid");
        }
        if (label == null || label.isBlank()) throw new IllegalArgumentException("label is required");
        this.goalId = goalId;
        this.label = label.trim();
        this.coverage = Objects.requireNonNull(coverage, "coverage");
        this.presentation = presentation;
        this.notices = List.copyOf(Objects.requireNonNull(notices, "notices"));
        this.continuation = continuation;
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
    public ContinuationReference getContinuation() { return continuation; }
    public enum Coverage { FULL, PARTIAL, NONE }
}
