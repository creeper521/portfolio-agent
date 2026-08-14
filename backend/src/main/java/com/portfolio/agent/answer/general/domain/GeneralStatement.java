package com.portfolio.agent.answer.general.domain;

import java.util.Set;

public final class GeneralStatement {
    private final String statementAlias;
    private final String text;
    private final GeneralStatementRole role;
    private final Set<String> conceptTags;
    private final GeneralSupportKind supportKind;

    public GeneralStatement(String statementAlias, String text, GeneralStatementRole role,
                            Set<String> conceptTags, GeneralSupportKind supportKind) {
        this.statementAlias = requireText(statementAlias, "statementAlias");
        this.text = requireText(text, "text");
        this.role = role;
        this.conceptTags = conceptTags == null ? Set.of() : Set.copyOf(conceptTags);
        this.supportKind = supportKind;
        if (role == null || supportKind == null) throw new IllegalArgumentException("role/supportKind required");
    }
    public String getStatementAlias() { return statementAlias; }
    public String getText() { return text; }
    public GeneralStatementRole getRole() { return role; }
    public Set<String> getConceptTags() { return conceptTags; }
    public GeneralSupportKind getSupportKind() { return supportKind; }
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
}
