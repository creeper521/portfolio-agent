package com.portfolio.agent.evaluation.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Iterator;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Privacy-safe structural snapshot of an stp-v1 response.
 *
 * <p>The factory reads an {@code agentTurn} JSON subtree transiently and retains
 * only closed statuses and integer counters. It never retains labels, subjects,
 * task identifiers, plan identifiers, fingerprints, confirmation envelopes, or
 * answer text.</p>
 */
public final class EvalSemanticTurnShape {

    private static final Pattern DISPLAY_INDEX =
            Pattern.compile("(?<![0-9])[0-9]{2}(?![0-9])");

    public enum Disposition {
        READY,
        PARTIAL_READY,
        CONFIRMATION_REQUIRED,
        CLARIFICATION_REQUIRED,
        BOUNDARY,
        REJECTED,
        PLAN_INVALIDATED,
        CONTEXT_INVALIDATED,
        UNKNOWN
    }

    public enum PlanOutcome {
        SUCCEEDED,
        PARTIAL,
        NO_RESULT,
        FAILED,
        CANCELLED,
        NONE,
        UNKNOWN
    }

    private final Disposition disposition;
    private final PlanOutcome planOutcome;
    private final int taskCount;
    private final int dependencyCount;
    private final int modelCallCount;
    private final int answeredCount;
    private final int blockedCount;
    private final int failedCount;
    private final int degradedCount;
    private final int portfolioSourceTaskCount;
    private final int generalSourceTaskCount;
    private final int synthesisSourceTaskCount;
    private final boolean planInvariantValid;
    private final boolean provenanceValid;
    private final boolean privacySafe;

    private EvalSemanticTurnShape(
            Disposition disposition,
            PlanOutcome planOutcome,
            int taskCount,
            int dependencyCount,
            int modelCallCount,
            int answeredCount,
            int blockedCount,
            int failedCount,
            int degradedCount,
            int portfolioSourceTaskCount,
            int generalSourceTaskCount,
            int synthesisSourceTaskCount,
            boolean planInvariantValid,
            boolean provenanceValid,
            boolean privacySafe) {
        this.disposition = Objects.requireNonNull(disposition, "disposition");
        this.planOutcome = Objects.requireNonNull(planOutcome, "planOutcome");
        this.taskCount = taskCount;
        this.dependencyCount = dependencyCount;
        this.modelCallCount = modelCallCount;
        this.answeredCount = answeredCount;
        this.blockedCount = blockedCount;
        this.failedCount = failedCount;
        this.degradedCount = degradedCount;
        this.portfolioSourceTaskCount = portfolioSourceTaskCount;
        this.generalSourceTaskCount = generalSourceTaskCount;
        this.synthesisSourceTaskCount = synthesisSourceTaskCount;
        this.planInvariantValid = planInvariantValid;
        this.provenanceValid = provenanceValid;
        this.privacySafe = privacySafe;
    }

    public static EvalSemanticTurnShape empty() {
        return new EvalSemanticTurnShape(
                Disposition.UNKNOWN, PlanOutcome.NONE,
                0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, true, true, true);
    }

    /**
     * Captures the structural part of an {@code agentTurn} response subtree.
     * Missing or unknown fields are represented as invalid/unknown structure,
     * never as a guessed success.
     */
    public static EvalSemanticTurnShape from(JsonNode agentTurn) {
        if (agentTurn == null || agentTurn.isNull() || !agentTurn.isObject()) {
            return empty();
        }
        Disposition disposition = parseDisposition(text(agentTurn, "disposition"));
        JsonNode plan = object(agentTurn, "plan");
        JsonNode outcome = object(agentTurn, "outcome");
        JsonNode summary = object(outcome, "taskSummary");
        int taskCount = nonNegativeInt(plan, "taskCount");
        int dependencyCount = dependencyCount(plan);
        int answeredCount = nonNegativeInt(summary, "answeredCount");
        int blockedCount = nonNegativeInt(summary, "blockedCount");
        int failedCount = nonNegativeInt(summary, "failedCount");
        int degradedCount = nonNegativeInt(summary, "degradedCount");
        SourceCounts sourceCounts = sourceCounts(summary, plan);
        boolean planInvariantValid = planInvariantValid(
                disposition, plan, summary, taskCount, answeredCount, blockedCount,
                failedCount, degradedCount);
        boolean provenanceValid = sourceCounts.valid
                && (taskCount == 0 || sourceCounts.total() == taskCount);
        return new EvalSemanticTurnShape(
                disposition,
                parsePlanOutcome(text(outcome, "planOutcome")),
                taskCount,
                dependencyCount,
                0,
                answeredCount,
                blockedCount,
                failedCount,
                degradedCount,
                sourceCounts.portfolio,
                sourceCounts.general,
                sourceCounts.synthesis,
                planInvariantValid,
                provenanceValid,
                !containsInternalField(agentTurn));
    }

    private static boolean planInvariantValid(
            Disposition disposition,
            JsonNode plan,
            JsonNode summary,
            int taskCount,
            int answeredCount,
            int blockedCount,
            int failedCount,
            int degradedCount) {
        if (plan == null) {
            return disposition == Disposition.CLARIFICATION_REQUIRED
                    || disposition == Disposition.BOUNDARY
                    || disposition == Disposition.REJECTED
                    || disposition == Disposition.PLAN_INVALIDATED
                    || disposition == Disposition.CONTEXT_INVALIDATED
                    || disposition == Disposition.UNKNOWN;
        }
        JsonNode tasks = plan.get("tasks");
        if (!tasks.isArray() || taskCount < 1 || taskCount > 6
                || tasks.size() != taskCount) {
            return false;
        }
        if (summary == null) {
            return disposition == Disposition.CONFIRMATION_REQUIRED;
        }
        int totalCount = nonNegativeInt(summary, "totalCount");
        if (totalCount != taskCount) {
            return false;
        }
        return answeredCount <= totalCount
                && blockedCount <= totalCount
                && failedCount <= totalCount
                && degradedCount <= totalCount;
    }

    private static int dependencyCount(JsonNode plan) {
        if (plan == null || !plan.path("tasks").isArray()) {
            return 0;
        }
        int count = 0;
        for (JsonNode task : plan.path("tasks")) {
            String summary = text(task, "dependencySummary");
            if (summary == null) {
                continue;
            }
            Matcher matcher = DISPLAY_INDEX.matcher(summary);
            while (matcher.find()) {
                count++;
            }
        }
        return count;
    }

    private static SourceCounts sourceCounts(JsonNode summary, JsonNode plan) {
        JsonNode items = summary == null ? null : summary.get("items");
        if (items != null && items.isArray() && !items.isEmpty()) {
            return sourceCountsFrom(items);
        }
        JsonNode tasks = plan == null ? null : plan.get("tasks");
        return tasks != null && tasks.isArray() ? sourceCountsFrom(tasks) : new SourceCounts();
    }

    private static SourceCounts sourceCountsFrom(JsonNode values) {
        SourceCounts counts = new SourceCounts();
        for (JsonNode value : values) {
            String sourceDomain = text(value, "sourceDomain");
            if ("PORTFOLIO".equals(sourceDomain)) {
                counts.portfolio++;
            } else if ("GENERAL".equals(sourceDomain)) {
                counts.general++;
            } else if ("SYNTHESIS".equals(sourceDomain)) {
                counts.synthesis++;
            } else {
                counts.valid = false;
            }
        }
        return counts;
    }

    private static boolean containsInternalField(JsonNode node) {
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if ("taskId".equals(name)
                    || "dependencyType".equals(name)
                    || "modelConfidence".equals(name)) {
                return true;
            }
            JsonNode child = node.get(name);
            if (child != null && child.isContainerNode() && containsInternalField(child)) {
                return true;
            }
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsInternalField(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static JsonNode object(JsonNode parent, String field) {
        if (parent == null) {
            return null;
        }
        JsonNode value = parent.get(field);
        return value != null && value.isObject() ? value : null;
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private static int nonNegativeInt(JsonNode node, String field) {
        if (node == null) {
            return 0;
        }
        JsonNode value = node.get(field);
        return value != null && value.canConvertToInt() && value.asInt() >= 0
                ? value.asInt() : 0;
    }

    private static Disposition parseDisposition(String value) {
        try {
            return value == null ? Disposition.UNKNOWN : Disposition.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return Disposition.UNKNOWN;
        }
    }

    private static PlanOutcome parsePlanOutcome(String value) {
        try {
            return value == null ? PlanOutcome.NONE : PlanOutcome.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return PlanOutcome.UNKNOWN;
        }
    }

    public Disposition getDisposition() { return disposition; }
    public PlanOutcome getPlanOutcome() { return planOutcome; }
    public int getTaskCount() { return taskCount; }
    public int getDependencyCount() { return dependencyCount; }
    public int getModelCallCount() { return modelCallCount; }
    public int getAnsweredCount() { return answeredCount; }
    public int getBlockedCount() { return blockedCount; }
    public int getFailedCount() { return failedCount; }
    public int getDegradedCount() { return degradedCount; }
    public int getPortfolioSourceTaskCount() { return portfolioSourceTaskCount; }
    public int getGeneralSourceTaskCount() { return generalSourceTaskCount; }
    public int getSynthesisSourceTaskCount() { return synthesisSourceTaskCount; }
    public boolean isPlanInvariantValid() { return planInvariantValid; }
    public boolean isProvenanceValid() { return provenanceValid; }
    public boolean isPrivacySafe() { return privacySafe; }

    @Override
    public String toString() {
        return "EvalSemanticTurnShape{disposition=" + disposition
                + ", planOutcome=" + planOutcome
                + ", taskCount=" + taskCount
                + ", dependencyCount=" + dependencyCount
                + ", modelCallCount=" + modelCallCount
                + ", answeredCount=" + answeredCount
                + ", blockedCount=" + blockedCount
                + ", failedCount=" + failedCount
                + ", degradedCount=" + degradedCount
                + ", portfolioSourceTaskCount=" + portfolioSourceTaskCount
                + ", generalSourceTaskCount=" + generalSourceTaskCount
                + ", synthesisSourceTaskCount=" + synthesisSourceTaskCount
                + ", planInvariantValid=" + planInvariantValid
                + ", provenanceValid=" + provenanceValid
                + ", privacySafe=" + privacySafe + '}';
    }

    private static final class SourceCounts {
        private int portfolio;
        private int general;
        private int synthesis;
        private boolean valid = true;

        private int total() {
            return portfolio + general + synthesis;
        }
    }
}
