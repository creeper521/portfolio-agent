package com.portfolio.agent.answer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portfolio.agent.answer.domain.AnswerSupportKind;
import java.util.List;

public final class AnswerBlockSupportResponse {
    private final AnswerSupportKind kind;
    private final List<StatementSupportReferenceResponse> statementReferences;
    private final List<String> sourceTaskIds;
    private final List<String> publicSourceKeys;
    private final String contentVersion;

    public AnswerBlockSupportResponse(
            String kind,
            List<StatementSupportReferenceResponse> statementReferences,
            List<String> sourceTaskIds,
            List<String> publicSourceKeys,
            String contentVersion) {
        this(AnswerSupportKind.valueOf(kind), statementReferences, sourceTaskIds,
                publicSourceKeys, contentVersion);
    }

    public AnswerBlockSupportResponse(
            AnswerSupportKind kind,
            List<StatementSupportReferenceResponse> statementReferences,
            List<String> sourceTaskIds,
            List<String> publicSourceKeys,
            String contentVersion) {
        this.kind = kind;
        this.statementReferences = List.copyOf(statementReferences);
        this.sourceTaskIds = List.copyOf(sourceTaskIds);
        this.publicSourceKeys = List.copyOf(publicSourceKeys);
        this.contentVersion = contentVersion;
    }

    public AnswerSupportKind getKind() { return kind; }
    public List<StatementSupportReferenceResponse> getStatementReferences() { return statementReferences; }
    public List<String> getSourceTaskIds() { return sourceTaskIds; }
    public List<String> getPublicSourceKeys() { return publicSourceKeys; }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getContentVersion() { return contentVersion; }
}
