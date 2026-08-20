package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres;

import com.portfolio.agent.turn.capability.portfolio.retrieval.RetrievalAttemptFailure;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;

import java.sql.SQLTimeoutException;

public final class PostgresRetrievalFailureClassifier {

    private PostgresRetrievalFailureClassifier() {
    }

    public static RetrievalAttemptFailure classify(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof QueryTimeoutException
                    || current instanceof SQLTimeoutException) {
                return RetrievalAttemptFailure.BACKEND_TIMEOUT;
            }
            if (current instanceof InvalidDataAccessResourceUsageException) {
                return RetrievalAttemptFailure.INTEGRITY_FAILURE;
            }
            if (current instanceof DataIntegrityViolationException) {
                return RetrievalAttemptFailure.INTEGRITY_FAILURE;
            }
            if (current instanceof DataAccessResourceFailureException
                    || current instanceof TransientDataAccessResourceException) {
                return RetrievalAttemptFailure.BACKEND_CONNECTION_UNAVAILABLE;
            }
            current = current.getCause();
        }
        return RetrievalAttemptFailure.INTEGRITY_FAILURE;
    }
}
