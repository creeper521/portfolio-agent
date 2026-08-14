package com.portfolio.agent.answer.domain;

import java.util.List;
import java.util.Objects;

public final class StatementSupportReference {

    private final String statementId;
    private final List<String> publicSourceKeys;
    private final String contentVersion;

    public StatementSupportReference(
            String statementId, List<String> publicSourceKeys, String contentVersion) {
        this.statementId = requireText(statementId, "statementId");
        this.publicSourceKeys = List.copyOf(Objects.requireNonNull(publicSourceKeys, "publicSourceKeys"));
        this.contentVersion = requireText(contentVersion, "contentVersion");
    }

    public String getStatementId() { return statementId; }
    public List<String> getPublicSourceKeys() { return publicSourceKeys; }
    public String getContentVersion() { return contentVersion; }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
