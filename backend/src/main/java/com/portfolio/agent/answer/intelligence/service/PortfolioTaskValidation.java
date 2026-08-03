package com.portfolio.agent.answer.intelligence.service;

import com.portfolio.agent.answer.intelligence.domain.PortfolioClarification;
import java.util.Objects;

public final class PortfolioTaskValidation {

    private final PortfolioClarification clarification;

    private PortfolioTaskValidation(PortfolioClarification clarification) {
        this.clarification = clarification;
    }

    public static PortfolioTaskValidation valid() {
        return new PortfolioTaskValidation(null);
    }

    public static PortfolioTaskValidation clarification(PortfolioClarification clarification) {
        return new PortfolioTaskValidation(Objects.requireNonNull(clarification, "clarification"));
    }

    public boolean isValid() {
        return clarification == null;
    }

    public PortfolioClarification getClarification() {
        return clarification;
    }
}
