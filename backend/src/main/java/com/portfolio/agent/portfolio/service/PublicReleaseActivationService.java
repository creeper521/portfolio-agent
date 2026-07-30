package com.portfolio.agent.portfolio.service;

import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionOperations;

public final class PublicReleaseActivationService {

    private static final String ACTIVATE_RELEASE_SQL = """
            WITH verified_release AS (
                UPDATE content_release
                SET status = 'PUBLISHED', published_at = now()
                WHERE release_id = CAST(? AS uuid)
                  AND status = 'VERIFIED'
                RETURNING release_id
            )
            INSERT INTO active_release (singleton, release_id, activated_at)
            SELECT true, release_id, now()
            FROM verified_release
            ON CONFLICT (singleton) DO UPDATE
            SET release_id = EXCLUDED.release_id,
                activated_at = EXCLUDED.activated_at
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionOperations transactions;

    public PublicReleaseActivationService(JdbcTemplate jdbcTemplate, TransactionOperations transactions) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    public PublicReleaseActivationResult activate(String releaseId) {
        validateReleaseId(releaseId);
        return transactions.execute(status -> activateInTransaction(releaseId));
    }

    private PublicReleaseActivationResult activateInTransaction(String releaseId) {
        int updatedRows = jdbcTemplate.update(ACTIVATE_RELEASE_SQL, releaseId);
        if (updatedRows != 1) {
            throw new IllegalStateException("only a VERIFIED release may be activated");
        }
        return new PublicReleaseActivationResult(releaseId, "PUBLISHED");
    }

    private void validateReleaseId(String releaseId) {
        try {
            UUID.fromString(releaseId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("release ID must be a UUID", exception);
        }
    }
}
