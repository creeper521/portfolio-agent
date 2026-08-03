package com.portfolio.agent.answer.intelligence.gateway;

public final class PortfolioRetrievalException extends RuntimeException {

    private final PortfolioRetrievalFailureKind kind;

    public PortfolioRetrievalException(
            PortfolioRetrievalFailureKind kind,
            String message,
            Throwable cause) {
        super(message, cause);
        this.kind = java.util.Objects.requireNonNull(kind, "kind");
    }

    public PortfolioRetrievalFailureKind getKind() { return kind; }
}
