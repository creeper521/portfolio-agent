package com.portfolio.agent.answer.composition.domain;

import com.portfolio.agent.answer.domain.GroundedAnswerContribution;
import com.portfolio.agent.answer.domain.PublicSourceReferenceValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

public abstract sealed class PortfolioAnswerMaterial
        permits FactAnswerMaterial, ComparisonAnswerMaterial, RecommendationAnswerMaterial {
    private final String publicTitle;
    private final List<String> fixedCaveats;
    private final List<String> omittedTopicLabels;

    protected PortfolioAnswerMaterial(String publicTitle, List<String> fixedCaveats,
            List<String> omittedTopicLabels) {
        this.publicTitle = DomainValues.requireText(publicTitle, "publicTitle");
        this.fixedCaveats = DomainValues.distinctTextCopy(fixedCaveats, "fixedCaveats");
        this.omittedTopicLabels = DomainValues.distinctTextCopy(
                omittedTopicLabels, "omittedTopicLabels");
    }

    public abstract MaterialKind getMaterialKind();
    public abstract List<ExpressionStatement> getExpressionStatements();
    public abstract List<String> getPublicSubjectLabels();

    public final List<GroundedStatement> getStatements() {
        return getExpressionStatements().stream().map(ExpressionStatement::getStatement).toList();
    }

    public final String getPublicTitle() { return publicTitle; }
    public final List<String> getFixedCaveats() { return fixedCaveats; }
    public final List<String> getOmittedTopicLabels() { return omittedTopicLabels; }

    public final GroundedAnswerContribution toGroundedContribution() {
        List<String> statements = getStatements().stream()
                .map(GroundedStatement::getPublicStatement).distinct().toList();
        LinkedHashMap<String, PublicSourceReferenceValue> sourceValues = new LinkedHashMap<>();
        getStatements().forEach(statement -> statement.getPublicSourceReferences().forEach(source ->
                sourceValues.putIfAbsent(source.getReferenceKey(), source)));
        List<String> sourceKeys = List.copyOf(sourceValues.keySet());
        return new GroundedAnswerContribution(statements, sourceKeys,
                new ArrayList<>(sourceValues.values()), fixedCaveats, omittedTopicLabels);
    }

    protected static void requireUniqueOrders(List<ExpressionStatement> entries, String name) {
        LinkedHashSet<Integer> orders = new LinkedHashSet<>();
        for (ExpressionStatement entry : entries) {
            if (!orders.add(entry.getStableOrder())) {
                throw new IllegalArgumentException(name + " contains duplicate stableOrder");
            }
        }
    }
}
