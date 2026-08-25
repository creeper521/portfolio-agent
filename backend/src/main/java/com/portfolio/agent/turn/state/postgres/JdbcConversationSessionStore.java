package com.portfolio.agent.turn.state.postgres;

import com.portfolio.agent.turn.continuation.ConversationSessionStore;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * PostgreSQL 会话存储（conversation_session 表）：按 ResumeToken 哈希读取与
 * upsert 匿名会话行。
 *
 * <p>find 支持多哈希（当前 + previous 密钥世代）命中且只接受未吊销、未绝对过期、
 * 密钥仍在支持集内的行；可选注入 {@link AgentStatePayloadCodec} 解密行内语义
 * 状态密文。所有语句在事务内执行并设置局部 statement_timeout（与 TurnDeadline
 * 取小），防止会话操作挤占 Turn 预算。</p>
 */
public final class JdbcConversationSessionStore implements ConversationSessionStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final String table;
    private final String tokenKeyId;
    private final Set<String> supportedTokenKeyIds;
    private final Clock clock;
    private final Duration databaseOperationTimeout;
    private final JdbcConversationSessionWriter writer;
    private final AgentStatePayloadCodec payloadCodec;
    public JdbcConversationSessionStore(
            JdbcTemplate jdbc, TransactionTemplate transactions,
            String schema, String tokenKeyId) {
        this(jdbc, transactions, schema, tokenKeyId, Set.of(tokenKeyId));
    }
    public JdbcConversationSessionStore(
            JdbcTemplate jdbc, TransactionTemplate transactions,
            String schema, String tokenKeyId, Set<String> supportedTokenKeyIds) {
        this(jdbc, transactions, schema, tokenKeyId, supportedTokenKeyIds, Clock.systemUTC());
    }
    public JdbcConversationSessionStore(
            JdbcTemplate jdbc, TransactionTemplate transactions,
            String schema, String tokenKeyId, Set<String> supportedTokenKeyIds,
            Clock clock) {
        this(jdbc, transactions, schema, tokenKeyId, supportedTokenKeyIds,
                Duration.ofSeconds(3), clock, null);
    }
    public JdbcConversationSessionStore(
            JdbcTemplate jdbc, TransactionTemplate transactions,
            String schema, String tokenKeyId, Set<String> supportedTokenKeyIds,
            Duration databaseOperationTimeout, Clock clock) {
        this(jdbc, transactions, schema, tokenKeyId, supportedTokenKeyIds,
                databaseOperationTimeout, clock, null);
    }
    public JdbcConversationSessionStore(
            JdbcTemplate jdbc, TransactionTemplate transactions,
            String schema, String tokenKeyId, Set<String> supportedTokenKeyIds,
            Duration databaseOperationTimeout, Clock clock,
            AgentStatePayloadCodec payloadCodec) {
        this.jdbc = jdbc; this.transactions = transactions;
        if (schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("schema is invalid");
        }
        if (tokenKeyId == null || tokenKeyId.isBlank()) {
            throw new IllegalArgumentException("tokenKeyId is required");
        }
        this.table = schema + ".conversation_session";
        this.tokenKeyId = tokenKeyId;
        this.writer = new JdbcConversationSessionWriter(
                jdbc, table, tokenKeyId);
        this.supportedTokenKeyIds = Set.copyOf(supportedTokenKeyIds);
        if (!this.supportedTokenKeyIds.contains(tokenKeyId)) {
            throw new IllegalArgumentException("current token key must be supported");
        }
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        if (databaseOperationTimeout == null || databaseOperationTimeout.isZero()
                || databaseOperationTimeout.isNegative()) {
            throw new IllegalArgumentException("databaseOperationTimeout is invalid");
        }
        this.databaseOperationTimeout = databaseOperationTimeout;
        this.payloadCodec = payloadCodec;
    }
    /**
     * 按一组可接受的令牌哈希查找会话：命中行必须未吊销、未绝对过期且密钥受支持；
     * 行内存在语义状态密文时用注入的 codec 解密（codec 缺失即抛错，fail-closed）。
     *
     * @return 命中的会话（含活跃讨论指针与语义状态）；无命中返回 empty
     */
    @Override public Optional<Session> find(
            java.util.List<byte[]> tokenHashes, Instant now,
            com.portfolio.agent.turn.execution.TurnDeadline deadline) {
        return transactions.execute(status -> {
            applyDatabaseTimeout(deadline);
            try {
                return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT conversation_id, resume_token_hash, created_at,"
                            + " absolute_expires_at, active_discussion_handle,"
                            + " active_discussion_project_id,"
                            + " active_discussion_expires_at, revision,"
                            + " semantic_state_key_id, semantic_state_nonce,"
                            + " semantic_state_ciphertext FROM " + table
                            + " WHERE resume_token_hash IN (" + placeholders(tokenHashes.size())
                            + ") AND revoked_at IS NULL"
                            + " AND absolute_expires_at>? AND token_key_id IN ("
                            + placeholders(supportedTokenKeyIds.size()) + ")",
                    (result, index) -> {
                        OffsetDateTime discussionExpiry = result.getObject(
                                "active_discussion_expires_at",
                                OffsetDateTime.class);
                        com.portfolio.agent.turn.continuation.ActiveDiscussionPointer pointer =
                                discussionExpiry == null ? null
                                        : new com.portfolio.agent.turn.continuation.ActiveDiscussionPointer(
                                        result.getString(
                                                "active_discussion_handle"),
                                        result.getString(
                                                "active_discussion_project_id"),
                                        discussionExpiry.toInstant());
                        String semanticKeyId = result.getString(
                                "semantic_state_key_id");
                        com.portfolio.agent.turn.continuation.ConversationSemanticState
                                semanticState = null;
                        if (semanticKeyId != null) {
                            if (payloadCodec == null) {
                                throw new IllegalStateException(
                                        "semantic state codec is unavailable");
                            }
                            semanticState = payloadCodec.decodeSemanticState(
                                    result.getObject("conversation_id", UUID.class)
                                            .toString(),
                                    new AgentStatePayloadCodec.Envelope(
                                            semanticKeyId,
                                            result.getBytes("semantic_state_nonce"),
                                            result.getBytes("semantic_state_ciphertext")));
                        }
                        return new Session(
                                result.getObject(
                                        "conversation_id", UUID.class)
                                        .toString(),
                                result.getBytes("resume_token_hash"),
                                result.getObject(
                                        "created_at", OffsetDateTime.class)
                                        .toInstant(),
                                result.getObject(
                                        "absolute_expires_at",
                                        OffsetDateTime.class).toInstant(),
                                pointer,
                                result.getLong("revision"), semanticState);
                    },
                        findParameters(tokenHashes, time(now), supportedTokenKeyIds)));
            } catch (EmptyResultDataAccessException missing) { return Optional.empty(); }
        });
    }
    /** 在事务内 upsert 会话行（换代条件见 JdbcConversationSessionWriter）。 */
    @Override public void save(Session session) {
        transactions.executeWithoutResult(status -> {
            applyDatabaseTimeout();
            writer.upsert(session);
        });
    }
    /** 测试辅助：按会话 ID 直接吊销会话行。 */
    void revokeForTest(String conversationId) {
        transactions.executeWithoutResult(status -> {
            applyDatabaseTimeout();
            jdbc.update("UPDATE " + table
                            + " SET revoked_at=? WHERE conversation_id=? AND revoked_at IS NULL",
                    time(clock.instant()), UUID.fromString(conversationId));
        });
    }
    /** 设置局部 statement_timeout（固定预算版，用于无 deadline 的写路径）。 */
    private void applyDatabaseTimeout() {
        jdbc.execute("SET LOCAL statement_timeout = " + databaseOperationTimeout.toMillis());
    }

    /** 设置局部 statement_timeout：取数据库预算与 Turn 剩余时间的较小者，已过期即抛错。 */
    private void applyDatabaseTimeout(
            com.portfolio.agent.turn.execution.TurnDeadline deadline) {
        long timeoutMillis = Math.min(
                databaseOperationTimeout.toMillis(), deadline.remainingMillis());
        if (timeoutMillis < 1) throw new IllegalStateException("session lookup deadline exceeded");
        jdbc.execute("SET LOCAL statement_timeout = " + timeoutMillis);
    }
    private String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }
    private Object[] parameters(Object first, Object second, Set<String> remaining) {
        Object[] values = new Object[2 + remaining.size()];
        values[0] = first;
        values[1] = second;
        int index = 2;
        for (String value : remaining) values[index++] = value;
        return values;
    }
    private Object[] findParameters(
            java.util.List<byte[]> hashes, Object now, Set<String> keyIds) {
        Object[] values = new Object[hashes.size() + 1 + keyIds.size()];
        int index = 0;
        for (byte[] hash : hashes) values[index++] = hash;
        values[index++] = now;
        for (String keyId : keyIds) values[index++] = keyId;
        return values;
    }
    private OffsetDateTime time(Instant value) { return value.atOffset(ZoneOffset.UTC); }
}
