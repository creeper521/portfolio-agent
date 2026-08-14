package com.portfolio.agent.answer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

public final class TaskSupportSummaryResponse {
    private final String kind;
    private final int statementCount;
    private final int publicSourceCount;
    private final int sourceTaskCount;
    private final String contentVersion;

    public TaskSupportSummaryResponse(
            String kind, int statementCount, int publicSourceCount,
            int sourceTaskCount, String contentVersion) {
        this.kind = kind;
        this.statementCount = statementCount;
        this.publicSourceCount = publicSourceCount;
        this.sourceTaskCount = sourceTaskCount;
        this.contentVersion = contentVersion;
    }

    public String getKind() { return kind; }
    public int getStatementCount() { return statementCount; }
    public int getPublicSourceCount() { return publicSourceCount; }
    public int getSourceTaskCount() { return sourceTaskCount; }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getContentVersion() { return contentVersion; }
}
