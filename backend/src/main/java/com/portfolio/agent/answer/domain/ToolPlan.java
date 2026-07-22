package com.portfolio.agent.answer.domain;

import java.util.List;
import java.util.Objects;

public final class ToolPlan {

    private final String toolPolicyVersion;
    private final String contentVersion;
    private final String runtimeBundleHash;
    private final QueryIntent queryIntent;
    private final List<ToolCall> calls;

    public ToolPlan(
            String toolPolicyVersion,
            String contentVersion,
            String runtimeBundleHash,
            QueryIntent queryIntent,
            List<ToolCall> calls
    ) {
        this.toolPolicyVersion = toolPolicyVersion;
        this.contentVersion = contentVersion;
        this.runtimeBundleHash = runtimeBundleHash;
        this.queryIntent = Objects.requireNonNull(queryIntent, "queryIntent");
        this.calls = List.copyOf(calls);
    }

    public String getToolPolicyVersion() { return toolPolicyVersion; }
    public String getContentVersion() { return contentVersion; }
    public String getRuntimeBundleHash() { return runtimeBundleHash; }
    public QueryIntent getQueryIntent() { return queryIntent; }
    public List<ToolCall> getCalls() { return calls; }
}
