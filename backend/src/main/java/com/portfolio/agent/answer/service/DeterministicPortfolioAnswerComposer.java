package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.PortfolioAnswerMaterial;
import com.portfolio.agent.answer.domain.PortfolioAnswerPlan;
import com.portfolio.agent.answer.domain.PortfolioAnswerSection;
import com.portfolio.agent.answer.exception.PortfolioAnswerCompositionException;

import java.util.List;
import java.util.Objects;

/** Deterministic composition facade for already validated P3 material. */
public final class DeterministicPortfolioAnswerComposer implements PortfolioAnswerComposer {

    @Override
    public PortfolioAnswerPlan compose(PortfolioAnswerMaterial material) {
        Objects.requireNonNull(material, "material");
        List<String> bodies = material.getStatements().stream()
                .map(com.portfolio.agent.answer.domain.GroundedStatement::getStatement)
                .toList();
        List<String> references = material.getStatements().stream()
                .flatMap(statement -> statement.getPublicSourceReferences().stream())
                .distinct()
                .toList();
        if (bodies.isEmpty()) {
            throw new PortfolioAnswerCompositionException(
                    "grounded material has no renderable statements");
        }
        AnswerSectionType sectionType = material.getKind()
                == PortfolioAnswerMaterial.MaterialKind.FACT
                ? AnswerSectionType.BACKGROUND : AnswerSectionType.BOUNDARY;
        PortfolioAnswerSection section = new PortfolioAnswerSection(
                sectionType, material.getTitle(), String.join("\n", bodies),
                List.of(), references);
        return new PortfolioAnswerPlan(material.getTitle(), null, List.of(section));
    }
}
