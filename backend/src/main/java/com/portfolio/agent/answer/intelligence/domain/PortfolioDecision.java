package com.portfolio.agent.answer.intelligence.domain;

import java.util.Objects;
import java.util.Optional;

public final class PortfolioDecision {

    private final PortfolioDisposition disposition;
    private final PortfolioIntelligenceResult material;

    public PortfolioDecision(
            PortfolioDisposition disposition,
            PortfolioIntelligenceResult material
    ) {
        this.disposition = Objects.requireNonNull(
                disposition,
                "disposition must not be null");
        if (disposition == PortfolioDisposition.NOT_PORTFOLIO && material != null) {
            throw new IllegalArgumentException(
                    "not-portfolio decision cannot carry material");
        }
        if (disposition != PortfolioDisposition.NOT_PORTFOLIO) {
            this.material = Objects.requireNonNull(
                    material,
                    "material must not be null for handled decision");
        } else {
            this.material = null;
        }
    }

    public PortfolioDisposition getDisposition() { return disposition; }
    public Optional<PortfolioIntelligenceResult> getMaterial() {
        return Optional.ofNullable(material);
    }
}
