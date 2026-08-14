package com.portfolio.agent.answer.synthesis.service;

import com.portfolio.agent.answer.synthesis.domain.AllowedRelation;

import java.util.List;
import java.util.stream.Collectors;

public final class DeterministicCrossDomainComposer {
    public String compose(String generalText, String portfolioText, AllowedRelation relation) {
        if (generalText == null || generalText.isBlank() || portfolioText == null
                || portfolioText.isBlank() || relation == null) {
            throw new IllegalArgumentException("synthesis inputs required");
        }
        return generalText.trim() + " — " + portfolioText.trim()
                + " (" + relation.getRelationType().name() + ")";
    }

    public List<String> composeAll(
            List<String> generalTexts, List<String> portfolioTexts, AllowedRelation relation) {
        if (generalTexts == null || portfolioTexts == null
                || generalTexts.isEmpty() || portfolioTexts.isEmpty()) {
            return List.of();
        }
        String general = join(generalTexts);
        String portfolio = join(portfolioTexts);
        if (general.isBlank() || portfolio.isBlank()) return List.of();
        return List.of(compose(general, portfolio, relation));
    }

    private String join(List<String> values) {
        return values.stream().filter(value -> value != null && !value.isBlank())
                .map(String::trim).collect(Collectors.joining("\n"));
    }
}
