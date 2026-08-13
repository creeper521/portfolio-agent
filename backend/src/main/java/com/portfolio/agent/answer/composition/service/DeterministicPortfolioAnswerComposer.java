package com.portfolio.agent.answer.composition.service;

import com.portfolio.agent.answer.composition.domain.ExpressionStatement;
import com.portfolio.agent.answer.composition.domain.PortfolioAnswerMaterial;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.PortfolioAnswerPlan;
import com.portfolio.agent.answer.domain.PortfolioAnswerSection;
import com.portfolio.agent.answer.domain.PublicSourceReferenceValue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Produces the complete, validated fallback before any expression attempt. */
public final class DeterministicPortfolioAnswerComposer {

    public PortfolioAnswerPlan compose(PortfolioAnswerMaterial material) {
        Objects.requireNonNull(material, "material");
        Map<AnswerSectionType, List<ExpressionStatement>> bySection = new LinkedHashMap<>();
        material.getExpressionStatements().stream()
                .sorted(Comparator.comparingInt(ExpressionStatement::getStableOrder))
                .forEach(entry -> bySection.computeIfAbsent(
                        entry.getAllowedSection(), ignored -> new ArrayList<>()).add(entry));
        List<PortfolioAnswerSection> sections = new ArrayList<>();
        bySection.forEach((sectionType, entries) -> sections.add(PortfolioAnswerSection.grounded(
                sectionType,
                sectionTitle(sectionType),
                entries.stream().map(entry -> entry.getStatement().getPublicStatement())
                        .distinct().reduce((left, right) -> left + "；" + right).orElseThrow(),
                stableReferences(entries))));
        List<String> boundary = new ArrayList<>(material.getFixedCaveats());
        boundary.addAll(material.getOmittedTopicLabels());
        if (!boundary.isEmpty()) {
            sections.add(new PortfolioAnswerSection(
                    AnswerSectionType.BOUNDARY, "边界", String.join("；", boundary),
                    List.of(), List.of()));
        }
        if (sections.isEmpty()) {
            throw new IllegalArgumentException("material has no renderable content");
        }
        return new PortfolioAnswerPlan(material.getPublicTitle(), null, sections);
    }

    private String sectionTitle(AnswerSectionType sectionType) {
        return switch (sectionType) {
            case BACKGROUND -> "背景";
            case RESPONSIBILITY -> "职责";
            case SOLUTION -> "方案";
            case VERIFICATION -> "验证";
            case STATUS -> "状态";
            case BOUNDARY -> "边界";
            case REJECTED -> throw new IllegalArgumentException("rejected section is not renderable");
        };
    }

    private List<PublicSourceReferenceValue> stableReferences(List<ExpressionStatement> entries) {
        LinkedHashMap<String, PublicSourceReferenceValue> byKey = new LinkedHashMap<>();
        entries.stream().flatMap(entry -> entry.getStatement().getPublicSourceReferences().stream())
                .forEach(reference -> byKey.putIfAbsent(reference.getReferenceKey(), reference));
        return List.copyOf(byKey.values());
    }
}
