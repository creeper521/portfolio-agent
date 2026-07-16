package com.portfolio.agent.portfolio.infrastructure.json;

public class InvalidPortfolioSnapshotException extends RuntimeException {

    public InvalidPortfolioSnapshotException(String message) {
        super(message);
    }

    public InvalidPortfolioSnapshotException(String message, Throwable cause) {
        super(message, cause);
    }
}
