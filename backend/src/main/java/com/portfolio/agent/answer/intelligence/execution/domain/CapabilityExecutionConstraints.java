package com.portfolio.agent.answer.intelligence.execution.domain;

import com.portfolio.agent.answer.routing.domain.TaskExecutionAllowance;

import java.util.Objects;

/** Capability-facing projection of the immutable task allowance. */
public final class CapabilityExecutionConstraints {

    private final TaskExecutionAllowance allowance;

    public CapabilityExecutionConstraints(TaskExecutionAllowance allowance) {
        this.allowance = Objects.requireNonNull(allowance, "allowance");
    }

    public TaskExecutionAllowance getAllowance() {
        return allowance;
    }

    @Override
    public String toString() {
        return "CapabilityExecutionConstraints{hasAllowance=true}";
    }
}
