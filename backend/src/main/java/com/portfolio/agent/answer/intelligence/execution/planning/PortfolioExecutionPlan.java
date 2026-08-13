package com.portfolio.agent.answer.intelligence.execution.planning;

import com.portfolio.agent.answer.intelligence.execution.domain.PortfolioEvidenceInvocation;

import java.util.List;
import java.util.Objects;

/** Untrusted but fully typed plan produced by the deterministic P3 planner. */
public final class PortfolioExecutionPlan {

    private final String taskId;
    private final List<PlannedInvocation> invocations;

    public PortfolioExecutionPlan(String taskId, List<PlannedInvocation> invocations) {
        this.taskId = requireText(taskId, "taskId");
        this.invocations = List.copyOf(Objects.requireNonNull(invocations, "invocations"));
        if (this.invocations.size() != 1) {
            throw new IllegalArgumentException("a portfolio plan must contain exactly one invocation");
        }
    }

    public String getTaskId() {
        return taskId;
    }

    public List<PlannedInvocation> getInvocations() {
        return invocations;
    }

    public PlannedInvocation getInvocation() {
        return invocations.getFirst();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PortfolioExecutionPlan that)) {
            return false;
        }
        return taskId.equals(that.taskId) && invocations.equals(that.invocations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId, invocations);
    }

    @Override
    public String toString() {
        return "PortfolioExecutionPlan{taskIdPresent=true, invocationCount="
                + invocations.size() + '}';
    }

    public static final class PlannedInvocation {

        private final String capabilityId;
        private final PortfolioEvidenceInvocation invocation;

        public PlannedInvocation(String capabilityId, PortfolioEvidenceInvocation invocation) {
            this.capabilityId = requireText(capabilityId, "capabilityId");
            this.invocation = Objects.requireNonNull(invocation, "invocation");
        }

        public String getCapabilityId() {
            return capabilityId;
        }

        public PortfolioEvidenceInvocation getInvocation() {
            return invocation;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PlannedInvocation that)) {
                return false;
            }
            return capabilityId.equals(that.capabilityId) && invocation.equals(that.invocation);
        }

        @Override
        public int hashCode() {
            return Objects.hash(capabilityId, invocation);
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
