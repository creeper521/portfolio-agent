package com.portfolio.agent.answer.routing.domain;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.RequestedOutput;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Candidate semantic plan. It is immutable, but it becomes trusted only after
 * {@code SemanticPlanValidator} returns a trusted plan view.
 */
public final class SemanticTurnPlan {

    private final String planId;
    private final String contentVersion;
    private final PlanSource source;
    private final List<SemanticTask> tasks;
    private final List<TaskDependency> dependencies;
    private final List<PlanExclusion> exclusions;
    private final Set<RequestedOutput> requestedOutputs;
    private final PlanConfirmationPolicy confirmationPolicy;
    private final String planFingerprint;

    public SemanticTurnPlan(
            String planId,
            String contentVersion,
            PlanSource source,
            List<SemanticTask> tasks,
            List<TaskDependency> dependencies,
            List<PlanExclusion> exclusions,
            Set<RequestedOutput> requestedOutputs,
            PlanConfirmationPolicy confirmationPolicy) {
        this(planId, contentVersion, source, tasks, dependencies, exclusions,
                requestedOutputs, confirmationPolicy, null);
    }

    public SemanticTurnPlan(
            String planId,
            String contentVersion,
            PlanSource source,
            List<SemanticTask> tasks,
            List<TaskDependency> dependencies,
            List<PlanExclusion> exclusions,
            Set<RequestedOutput> requestedOutputs,
            PlanConfirmationPolicy confirmationPolicy,
            String planFingerprint) {
        this.planId = requireText(planId, "planId");
        this.contentVersion = requireText(contentVersion, "contentVersion");
        this.source = Objects.requireNonNull(source, "source");
        this.tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks"));
        this.dependencies = List.copyOf(Objects.requireNonNull(dependencies, "dependencies"));
        this.exclusions = List.copyOf(Objects.requireNonNull(exclusions, "exclusions"));
        this.requestedOutputs = Set.copyOf(Objects.requireNonNull(requestedOutputs, "requestedOutputs"));
        this.confirmationPolicy = Objects.requireNonNull(confirmationPolicy, "confirmationPolicy");
        this.planFingerprint = normalizeText(planFingerprint);
    }

    public String getPlanId() {
        return planId;
    }

    public String getContentVersion() {
        return contentVersion;
    }

    public PlanSource getSource() {
        return source;
    }

    public List<SemanticTask> getTasks() {
        return tasks;
    }

    public List<TaskDependency> getDependencies() {
        return dependencies;
    }

    public List<PlanExclusion> getExclusions() {
        return exclusions;
    }

    public Set<RequestedOutput> getRequestedOutputs() {
        return requestedOutputs;
    }

    public PlanConfirmationPolicy getConfirmationPolicy() {
        return confirmationPolicy;
    }

    public String getPlanFingerprint() {
        return planFingerprint;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SemanticTurnPlan that)) {
            return false;
        }
        return Objects.equals(planId, that.planId)
                && Objects.equals(contentVersion, that.contentVersion)
                && source == that.source
                && Objects.equals(tasks, that.tasks)
                && Objects.equals(dependencies, that.dependencies)
                && Objects.equals(exclusions, that.exclusions)
                && Objects.equals(requestedOutputs, that.requestedOutputs)
                && Objects.equals(confirmationPolicy, that.confirmationPolicy)
                && Objects.equals(planFingerprint, that.planFingerprint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(planId, contentVersion, source, tasks, dependencies, exclusions,
                requestedOutputs, confirmationPolicy, planFingerprint);
    }

    @Override
    public String toString() {
        return "SemanticTurnPlan{source=" + source
                + ", taskCount=" + tasks.size()
                + ", dependencyCount=" + dependencies.size()
                + ", exclusionCount=" + exclusions.size()
                + ", requestedOutputCount=" + requestedOutputs.size()
                + ", confirmationRequired=" + confirmationPolicy.isConfirmationRequired() + '}';
    }

    public enum PlanSource {
        RULE,
        MODEL_ASSISTED,
        REFERENCE
    }

    public enum ConfirmationTrigger {
        TASK_COUNT_REQUIRES_CONFIRMATION,
        MEDIUM_CONFIDENCE_FIELD,
        MIXED_SOURCE_DOMAINS,
        INFERRED_DEPENDENCY,
        BROAD_SUBJECT_SCOPE,
        LARGE_OUTPUT_SCOPE,
        PARTIAL_EXECUTION,
        ORDER_ADJUSTED,
        NODE_CAPABILITY_BOUNDARY
    }

    public static final class PlanConfirmationPolicy {

        private final boolean confirmationRequired;
        private final Set<ConfirmationTrigger> triggerCodes;

        public PlanConfirmationPolicy(boolean confirmationRequired, Set<ConfirmationTrigger> triggerCodes) {
            this.confirmationRequired = confirmationRequired;
            this.triggerCodes = Set.copyOf(Objects.requireNonNull(triggerCodes, "triggerCodes"));
            if (confirmationRequired != !this.triggerCodes.isEmpty()) {
                throw new IllegalArgumentException(
                        "confirmationRequired must match the presence of confirmation trigger codes");
            }
        }

        public static PlanConfirmationPolicy noConfirmation() {
            return new PlanConfirmationPolicy(false, Set.of());
        }

        public static PlanConfirmationPolicy confirmationRequired(Set<ConfirmationTrigger> triggerCodes) {
            return new PlanConfirmationPolicy(true, triggerCodes);
        }

        public boolean isConfirmationRequired() {
            return confirmationRequired;
        }

        public Set<ConfirmationTrigger> getTriggerCodes() {
            return triggerCodes;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PlanConfirmationPolicy that)) {
                return false;
            }
            return confirmationRequired == that.confirmationRequired
                    && Objects.equals(triggerCodes, that.triggerCodes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(confirmationRequired, triggerCodes);
        }

        @Override
        public String toString() {
            return "PlanConfirmationPolicy{confirmationRequired=" + confirmationRequired
                    + ", triggerCount=" + triggerCodes.size() + '}';
        }
    }

    private static String requireText(String value, String name) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return normalized;
    }

    private static String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
