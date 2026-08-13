package com.portfolio.agent.answer.composition.gateway;

/** Signals a transport or provider-envelope failure without exposing provider details. */
public final class PortfolioExpressionProviderException extends RuntimeException {

    public PortfolioExpressionProviderException(String message) {
        super(message);
    }

    public PortfolioExpressionProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
