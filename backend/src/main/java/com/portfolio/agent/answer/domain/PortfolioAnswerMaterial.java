package com.portfolio.agent.answer.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** P1-facing material. It contains only grounded statements and public references. */
public final class PortfolioAnswerMaterial {
    public enum MaterialKind { FACT, COMPARISON, RECOMMENDATION }

    private final MaterialKind kind;
    private final String title;
    private final List<GroundedStatement> statements;
    private final List<String> caveats;
    private final List<String> omittedTopicLabels;

    public PortfolioAnswerMaterial(
            MaterialKind kind, String title, List<GroundedStatement> statements,
            List<String> caveats, List<String> omittedTopicLabels) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.title = requireText(title, "title");
        this.statements = List.copyOf(Objects.requireNonNull(statements, "statements"));
        this.caveats = distinct(caveats, "caveats");
        this.omittedTopicLabels = distinct(omittedTopicLabels, "omittedTopicLabels");
        if (this.statements.isEmpty() && this.omittedTopicLabels.isEmpty()) {
            throw new IllegalArgumentException("material must contain statements or omitted topics");
        }
    }

    public static PortfolioAnswerMaterial fromContribution(
            MaterialKind kind, String title, GroundedAnswerContribution contribution) {
        Objects.requireNonNull(contribution, "contribution");
        List<GroundedStatement> statements = contribution.getSupportedStatements().stream()
                .map(statement -> new GroundedStatement(statement,
                        contribution.getPublicSourceReferences(), contribution.getSourceReferences())).toList();
        return new PortfolioAnswerMaterial(kind, title, statements,
                contribution.getCaveats(), contribution.getOmittedTopicLabels());
    }

    public MaterialKind getKind() { return kind; }
    public String getTitle() { return title; }
    public List<GroundedStatement> getStatements() { return statements; }
    public List<String> getCaveats() { return caveats; }
    public List<String> getOmittedTopicLabels() { return omittedTopicLabels; }

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
