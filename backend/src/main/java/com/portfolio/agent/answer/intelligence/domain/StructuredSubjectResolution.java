package com.portfolio.agent.answer.intelligence.domain;

import java.util.Objects;

public final class StructuredSubjectResolution {

    private final StructuredSubjectResolutionType type;
    private final PortfolioTask task;

    private StructuredSubjectResolution(
            StructuredSubjectResolutionType type,
            PortfolioTask task
    ) {
        this.type = Objects.requireNonNull(type, "type");
        this.task = task;
    }

    public static StructuredSubjectResolution none() {
        return new StructuredSubjectResolution(
                StructuredSubjectResolutionType.NONE, null);
    }

    public static StructuredSubjectResolution matched(PortfolioTask task) {
        return new StructuredSubjectResolution(
                StructuredSubjectResolutionType.MATCHED,
                Objects.requireNonNull(task, "task"));
    }

    public static StructuredSubjectResolution invalid() {
        return new StructuredSubjectResolution(
                StructuredSubjectResolutionType.INVALID, null);
    }

    public StructuredSubjectResolutionType getType() {
        return type;
    }

    public PortfolioTask getTask() {
        return task;
    }
}
