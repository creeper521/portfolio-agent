package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.evaluation.domain.EvalProviderAuthorization;

import java.util.Objects;

/**
 * Per-run operational identity shared with executors; it contains no case oracle data.
 */
public final class EvalRunContext {

    private final String runId;
    private final String contentVersion;
    private final EvalProviderAuthorization providerAuthorization;

    public EvalRunContext(String runId, String contentVersion) {
        this(runId, contentVersion, EvalProviderAuthorization.NOT_AUTHORIZED);
    }

    public EvalRunContext(
            String runId,
            String contentVersion,
            EvalProviderAuthorization providerAuthorization) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.contentVersion = Objects.requireNonNull(contentVersion, "contentVersion");
        this.providerAuthorization = Objects.requireNonNull(
                providerAuthorization, "providerAuthorization");
    }

    public String getRunId() { return runId; }
    public String getContentVersion() { return contentVersion; }
    public EvalProviderAuthorization getProviderAuthorization() {
        return providerAuthorization;
    }
}
