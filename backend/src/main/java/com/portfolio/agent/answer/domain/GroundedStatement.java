package com.portfolio.agent.answer.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Public statement whose every claim is tied to public reference keys. */
public final class GroundedStatement {
    private final String statement;
    private final List<String> publicSourceReferences;
    private final List<PublicSourceReferenceValue> sourceReferences;

    public GroundedStatement(String statement, List<String> publicSourceReferences) {
        this(statement, publicSourceReferences, List.of());
    }

    public GroundedStatement(
            String statement,
            List<String> publicSourceReferences,
            List<PublicSourceReferenceValue> sourceReferences) {
        this.statement = requireText(statement, "statement");
        this.publicSourceReferences = distinct(publicSourceReferences, "publicSourceReferences");
        this.sourceReferences = List.copyOf(Objects.requireNonNull(sourceReferences, "sourceReferences"));
        if (this.publicSourceReferences.isEmpty()) {
            throw new IllegalArgumentException("grounded statement requires public sources");
        }
    }

    public String getStatement() { return statement; }
    public List<String> getPublicSourceReferences() { return publicSourceReferences; }
    public List<PublicSourceReferenceValue> getSourceReferences() { return sourceReferences; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GroundedStatement that)) return false;
        return statement.equals(that.statement)
                && publicSourceReferences.equals(that.publicSourceReferences)
                && sourceReferences.equals(that.sourceReferences);
    }
    @Override public int hashCode() { return Objects.hash(statement, publicSourceReferences, sourceReferences); }
    @Override public String toString() { return "GroundedStatement{hasPublicSources=true}"; }

    private static List<String> distinct(List<String> values, String name) {
        Objects.requireNonNull(values, name);
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " contains blank");
            if (!seen.add(value.trim())) throw new IllegalArgumentException(name + " contains duplicates");
        }
        return List.copyOf(new ArrayList<>(seen));
    }
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
