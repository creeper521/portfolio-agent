package com.portfolio.agent.answer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public final class StatementSupportReferenceResponse {
    private final String statementId;
    private final List<String> publicSourceKeys;
    private final String contentVersion;

    public StatementSupportReferenceResponse(
            String statementId, List<String> publicSourceKeys, String contentVersion) {
        this.statementId = statementId;
        this.publicSourceKeys = List.copyOf(publicSourceKeys);
        this.contentVersion = contentVersion;
    }

    public String getStatementId() { return statementId; }
    public List<String> getPublicSourceKeys() { return publicSourceKeys; }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getContentVersion() { return contentVersion; }
}
