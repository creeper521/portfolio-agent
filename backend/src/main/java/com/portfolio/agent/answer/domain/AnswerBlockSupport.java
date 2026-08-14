package com.portfolio.agent.answer.domain;

import java.util.List;
import java.util.Objects;

public final class AnswerBlockSupport {

    private final AnswerSupportKind kind;
    private final List<StatementSupportReference> statementReferences;
    private final List<String> sourceTaskIds;
    private final List<String> publicSourceKeys;
    private final String contentVersion;

    public AnswerBlockSupport(
            AnswerSupportKind kind,
            List<StatementSupportReference> statementReferences,
            List<String> sourceTaskIds,
            List<String> publicSourceKeys,
            String contentVersion) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.statementReferences = List.copyOf(Objects.requireNonNull(statementReferences, "statementReferences"));
        this.sourceTaskIds = List.copyOf(Objects.requireNonNull(sourceTaskIds, "sourceTaskIds"));
        this.publicSourceKeys = List.copyOf(Objects.requireNonNull(publicSourceKeys, "publicSourceKeys"));
        this.contentVersion = contentVersion == null || contentVersion.isBlank()
                ? null : contentVersion.trim();
        if (kind == AnswerSupportKind.VERIFIED_PUBLIC_EVIDENCE
                && (this.statementReferences.isEmpty() || this.publicSourceKeys.isEmpty())) {
            throw new IllegalArgumentException("verified support requires statements and public sources");
        }
        if (kind == AnswerSupportKind.GENERAL_KNOWLEDGE && !this.publicSourceKeys.isEmpty()) {
            throw new IllegalArgumentException("general support cannot carry portfolio sources");
        }
    }

    public AnswerSupportKind getKind() { return kind; }
    public List<StatementSupportReference> getStatementReferences() { return statementReferences; }
    public List<String> getSourceTaskIds() { return sourceTaskIds; }
    public List<String> getPublicSourceKeys() { return publicSourceKeys; }
    public String getContentVersion() { return contentVersion; }
}
