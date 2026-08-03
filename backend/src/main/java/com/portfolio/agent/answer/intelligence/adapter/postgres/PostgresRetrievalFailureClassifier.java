package com.portfolio.agent.answer.intelligence.adapter.postgres;

import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetrievalFailureKind;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;

import java.sql.SQLTimeoutException;

final class PostgresRetrievalFailureClassifier {

    private PostgresRetrievalFailureClassifier() {
    }

    static PortfolioRetrievalFailureKind classify(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof QueryTimeoutException
                    || current instanceof SQLTimeoutException) {
                return PortfolioRetrievalFailureKind.TIMEOUT;
            }
            if (current instanceof InvalidDataAccessResourceUsageException) {
                return PortfolioRetrievalFailureKind.INVALID_QUERY;
            }
            if (current instanceof DataIntegrityViolationException) {
                return PortfolioRetrievalFailureKind.DATA_CORRUPTION;
            }
            if (current instanceof DataAccessResourceFailureException
                    || current instanceof TransientDataAccessResourceException) {
                return PortfolioRetrievalFailureKind.CONNECTION_UNAVAILABLE;
            }
            current = current.getCause();
        }
        return PortfolioRetrievalFailureKind.CONTRACT_VIOLATION;
    }
}
