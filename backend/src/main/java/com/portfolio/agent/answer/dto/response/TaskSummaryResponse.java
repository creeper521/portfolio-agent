package com.portfolio.agent.answer.dto.response;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskSourceDomain;

import java.util.List;
import java.util.Objects;

/** Aggregate task status using user-facing labels rather than internal task outcome enums. */
public final class TaskSummaryResponse {

    private final String displayMode;
    private final int totalCount;
    private final int answeredCount;
    private final int notSupportedCount;
    private final int emptyCount;
    private final int blockedCount;
    private final int failedCount;
    private final int cancelledCount;
    private final int degradedCount;
    private final List<Item> items;

    public TaskSummaryResponse(
            String displayMode, int totalCount, int answeredCount, int notSupportedCount,
            int emptyCount, int blockedCount, int failedCount, int cancelledCount,
            int degradedCount, List<Item> items) {
        this.displayMode = requireText(displayMode, "displayMode");
        this.totalCount = totalCount;
        this.answeredCount = answeredCount;
        this.notSupportedCount = notSupportedCount;
        this.emptyCount = emptyCount;
        this.blockedCount = blockedCount;
        this.failedCount = failedCount;
        this.cancelledCount = cancelledCount;
        this.degradedCount = degradedCount;
        this.items = List.copyOf(Objects.requireNonNull(items, "items"));
    }

    public String getDisplayMode() { return displayMode; }
    public int getTotalCount() { return totalCount; }
    public int getAnsweredCount() { return answeredCount; }
    public int getNotSupportedCount() { return notSupportedCount; }
    public int getEmptyCount() { return emptyCount; }
    public int getBlockedCount() { return blockedCount; }
    public int getFailedCount() { return failedCount; }
    public int getCancelledCount() { return cancelledCount; }
    public int getDegradedCount() { return degradedCount; }
    public List<Item> getItems() { return items; }

    public static final class Item {
        private final String displayIndex;
        private final String goalLabel;
        private final String status;
        private final TaskSourceDomain sourceDomain;

        public Item(String displayIndex, String goalLabel, String status, TaskSourceDomain sourceDomain) {
            this.displayIndex = requireText(displayIndex, "displayIndex");
            this.goalLabel = requireText(goalLabel, "goalLabel");
            this.status = requireText(status, "status");
            this.sourceDomain = java.util.Objects.requireNonNull(sourceDomain, "sourceDomain");
        }

        public String getDisplayIndex() { return displayIndex; }
        public String getGoalLabel() { return goalLabel; }
        public String getStatus() { return status; }
        public TaskSourceDomain getSourceDomain() { return sourceDomain; }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
