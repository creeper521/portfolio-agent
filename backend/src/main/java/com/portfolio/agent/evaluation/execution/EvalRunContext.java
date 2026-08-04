package com.portfolio.agent.evaluation.execution;

import java.util.Objects;

/**
 * Per-run operational identity shared with executors; it contains no case oracle data.
 */
public final class EvalRunContext {

    private final String runId;
    private final String contentVersion;

    public EvalRunContext(String runId, String contentVersion) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.contentVersion = Objects.requireNonNull(contentVersion, "contentVersion");
    }

    public String getRunId() { return runId; }
    public String getContentVersion() { return contentVersion; }
}
