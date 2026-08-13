package com.portfolio.agent.answer.composition.projection;

import com.portfolio.agent.answer.composition.domain.CandidateReference;
import com.portfolio.agent.answer.composition.domain.ExpressionStatement;
import com.portfolio.agent.answer.composition.domain.GroundedStatement;
import com.portfolio.agent.answer.composition.domain.SubjectReference;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Request-local reverse map. Alias values must never be logged or persisted. */
public final class ExpressionAliasRegistry {
    private final Map<String, SubjectReference> subjects = new LinkedHashMap<>();
    private final Map<String, ExpressionStatement> statements = new LinkedHashMap<>();
    private final Map<String, String> dimensions = new LinkedHashMap<>();
    private final Map<String, CandidateReference> candidates = new LinkedHashMap<>();

    public void addSubject(String alias, SubjectReference subject) {
        put(alias, "P\\d{2}", subjects, subject);
    }
    public void addStatement(String alias, ExpressionStatement statement) {
        put(alias, "S\\d{3}", statements, statement);
    }
    public void addDimension(String alias, String dimensionKey) {
        put(alias, "D\\d{2}", dimensions, dimensionKey);
    }
    public void addCandidate(String alias, CandidateReference candidate) {
        put(alias, "C\\d{2}", candidates, candidate);
    }

    public SubjectReference subject(String alias) { return subjects.get(alias); }
    public GroundedStatement statement(String alias) {
        ExpressionStatement entry = statements.get(alias);
        return entry == null ? null : entry.getStatement();
    }
    public ExpressionStatement expressionStatement(String alias) { return statements.get(alias); }
    public String dimension(String alias) { return dimensions.get(alias); }
    public CandidateReference candidate(String alias) { return candidates.get(alias); }
    public boolean containsSubject(String alias) { return subjects.containsKey(alias); }
    public boolean containsStatement(String alias) { return statements.containsKey(alias); }
    public boolean containsDimension(String alias) { return dimensions.containsKey(alias); }
    public boolean containsCandidate(String alias) { return candidates.containsKey(alias); }
    public Set<String> subjectAliases() { return Set.copyOf(subjects.keySet()); }
    public Set<String> statementAliases() { return Set.copyOf(statements.keySet()); }
    public Set<String> dimensionAliases() { return Set.copyOf(dimensions.keySet()); }
    public Set<String> candidateAliases() { return Set.copyOf(candidates.keySet()); }
    public int subjectCount() { return subjects.size(); }
    public int statementCount() { return statements.size(); }
    public int dimensionCount() { return dimensions.size(); }
    public int candidateCount() { return candidates.size(); }

    public String aliasOf(SubjectReference target) {
        return findIdentityOrValue(subjects, target);
    }
    public String aliasOf(ExpressionStatement target) {
        return findIdentityOrValue(statements, target);
    }
    public String aliasOf(CandidateReference target) {
        return findIdentityOrValue(candidates, target);
    }

    @Override public String toString() {
        return "ExpressionAliasRegistry{subjectCount=" + subjects.size()
                + ", statementCount=" + statements.size()
                + ", dimensionCount=" + dimensions.size()
                + ", candidateCount=" + candidates.size() + '}';
    }

    private static <T> void put(String alias, String pattern, Map<String, T> map, T value) {
        if (alias == null || !alias.matches(pattern) || value == null) {
            throw new IllegalArgumentException("invalid request-local alias binding");
        }
        if (map.putIfAbsent(alias, value) != null) {
            throw new IllegalArgumentException("duplicate request-local alias");
        }
    }

    private static <T> String findIdentityOrValue(Map<String, T> values, T target) {
        for (Map.Entry<String, T> entry : values.entrySet()) {
            if (entry.getValue() == target || entry.getValue().equals(target)) return entry.getKey();
        }
        return null;
    }
}
