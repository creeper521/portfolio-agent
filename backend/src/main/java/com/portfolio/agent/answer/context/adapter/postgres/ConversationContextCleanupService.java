package com.portfolio.agent.answer.context.adapter.postgres;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Objects;

/** Bounded physical cleanup for expired Context sessions under a PostgreSQL advisory lock. */
public final class ConversationContextCleanupService {
    private static final long CLEANUP_LOCK_KEY = 4_918_227_331L;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactions;
    private final ConversationContextProperties properties;
    private final String schema;

    public ConversationContextCleanupService(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactions,
            ConversationContextProperties properties,
            ConversationContextDatabaseProperties databaseProperties) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.schema = Objects.requireNonNull(databaseProperties, "databaseProperties").getSchema();
    }

    public boolean cleanupExpired(Instant now) {
        Objects.requireNonNull(now, "now");
        Boolean cleaned = transactions.execute(status -> {
            Boolean lock = jdbcTemplate.queryForObject(
                    "SELECT pg_try_advisory_xact_lock(?)", Boolean.class, CLEANUP_LOCK_KEY);
            if (!Boolean.TRUE.equals(lock)) {
                return false;
            }
            int batch = Math.min(properties.getCleanupBatchSize(), 500);
            jdbcTemplate.update("DELETE FROM " + table("conversation_session")
                            + " WHERE conversation_id IN (SELECT conversation_id FROM "
                            + table("conversation_session")
                            + " WHERE (idle_expires_at <= ? OR absolute_expires_at <= ?) "
                            + "ORDER BY conversation_id LIMIT ?)",
                    java.sql.Timestamp.from(now), java.sql.Timestamp.from(now), batch);
            return true;
        });
        return Boolean.TRUE.equals(cleaned);
    }

    private String table(String name) {
        return schema + "." + name;
    }
}
