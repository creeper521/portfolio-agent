package com.portfolio.agent.answer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public final class StatementSupportReferenceResponse {
    private final String statementId;
    private final String sourceTaskId;
    private final List<String> publicSourceKeys;
    private final String contentVersion;

    public StatementSupportReferenceResponse(
            String statementId, List<String> publicSourceKeys, String contentVersion) {
        this(statementId, null, publicSourceKeys, contentVersion);
    }

    public StatementSupportReferenceResponse(
            String statementId, String sourceTaskId,
            List<String> publicSourceKeys, String contentVersion) {
        this.statementId = statementId;
        this.sourceTaskId = sourceTaskId;
        this.publicSourceKeys = List.copyOf(publicSourceKeys);
        this.contentVersion = contentVersion;
    }

    public String getStatementId() { return statementId; }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getSourceTaskId() { return sourceTaskId; }
    public List<String> getPublicSourceKeys() { return publicSourceKeys; }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getContentVersion() { return contentVersion; }
}
