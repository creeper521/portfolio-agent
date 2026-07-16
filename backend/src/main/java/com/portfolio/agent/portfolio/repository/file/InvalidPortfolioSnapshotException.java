package com.portfolio.agent.portfolio.repository.file;

public class InvalidPortfolioSnapshotException extends RuntimeException {

    public InvalidPortfolioSnapshotException(String message) {
        super(message);
    }

    public InvalidPortfolioSnapshotException(String message, Throwable cause) {
        super(message, cause);
    }
}
