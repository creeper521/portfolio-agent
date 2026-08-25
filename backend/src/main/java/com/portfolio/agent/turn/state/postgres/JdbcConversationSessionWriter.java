package com.portfolio.agent.turn.state.postgres;

import com.portfolio.agent.turn.continuation.ConversationSessionStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 会话行 upsert 的共享 SQL 写入器；事务边界仍由调用方持有。
 *
 * <p>核心是"换代条件"（见 {@link #replacement()}）：仅当旧行绝对过期且令牌哈希
 * 不同时，才允许新会话覆盖旧行的创建/过期/讨论/语义状态字段并清空讨论指针与
 * 吊销标记；未过期或同哈希的写入只做无冲突字段刷新。防止并发试探会话互相
 * 覆盖仍存活的会话状态。</p>
 */
final class JdbcConversationSessionWriter {
    private final JdbcTemplate jdbc;
    private final String table;
    private final String tokenKeyId;

    JdbcConversationSessionWriter(
            JdbcTemplate jdbc, String table, String tokenKeyId) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc, "jdbc");
        this.table = java.util.Objects.requireNonNull(table, "table");
        this.tokenKeyId = java.util.Objects.requireNonNull(
                tokenKeyId, "tokenKeyId");
    }

    void upsert(ConversationSessionStore.Session session) {
        jdbc.update(
                "INSERT INTO " + table + " AS existing"
                        + " (conversation_id, resume_token_hash, token_key_id,"
                        + " created_at, last_accessed_at, idle_expires_at,"
                        + " absolute_expires_at, context_count, payload_bytes,"
                        + " revision, revoked_at)"
                        + " VALUES (?,?,?,?,?,?,?,?,?,0,NULL)"
                        + " ON CONFLICT (conversation_id) DO UPDATE SET"
                        + " resume_token_hash=EXCLUDED.resume_token_hash,"
                        + " token_key_id=EXCLUDED.token_key_id,"
                        + " created_at=CASE WHEN " + replacement()
                        + " THEN EXCLUDED.created_at ELSE existing.created_at END,"
                        + " last_accessed_at=CASE WHEN " + replacement()
                        + " THEN EXCLUDED.created_at ELSE existing.last_accessed_at END,"
                        + " idle_expires_at=CASE WHEN " + replacement()
                        + " THEN EXCLUDED.absolute_expires_at ELSE existing.absolute_expires_at END,"
                        + " absolute_expires_at=CASE WHEN " + replacement()
                        + " THEN EXCLUDED.absolute_expires_at ELSE existing.absolute_expires_at END,"
                        + " active_discussion_handle=CASE WHEN " + replacement()
                        + " THEN NULL ELSE existing.active_discussion_handle END,"
                        + " active_discussion_project_id=CASE WHEN " + replacement()
                        + " THEN NULL ELSE existing.active_discussion_project_id END,"
                        + " active_discussion_expires_at=CASE WHEN " + replacement()
                        + " THEN NULL ELSE existing.active_discussion_expires_at END,"
                        + " semantic_state_key_id=CASE WHEN " + replacement()
                        + " THEN NULL ELSE existing.semantic_state_key_id END,"
                        + " semantic_state_nonce=CASE WHEN " + replacement()
                        + " THEN NULL ELSE existing.semantic_state_nonce END,"
                        + " semantic_state_ciphertext=CASE WHEN " + replacement()
                        + " THEN NULL ELSE existing.semantic_state_ciphertext END,"
                        + " semantic_state_updated_at=CASE WHEN " + replacement()
                        + " THEN NULL ELSE existing.semantic_state_updated_at END,"
                        + " revision=CASE WHEN " + replacement()
                        + " THEN existing.revision+1 ELSE existing.revision END,"
                        + " revoked_at=CASE WHEN " + replacement()
                        + " THEN NULL ELSE existing.revoked_at END"
                        + " WHERE (existing.revoked_at IS NULL"
                        + " AND existing.absolute_expires_at>EXCLUDED.created_at)"
                        + " OR (" + replacement() + ")",
                UUID.fromString(session.conversationId()),
                session.tokenHash(), tokenKeyId,
                time(session.createdAt()), time(session.createdAt()),
                time(session.expiresAt()), time(session.expiresAt()), 0, 0);
    }

    /** 换代条件 SQL 片段：旧行绝对已过期且 ResumeToken 哈希不同才视为新一代会话。 */
    private String replacement() {
        return "existing.absolute_expires_at<=EXCLUDED.created_at"
                + " AND existing.resume_token_hash<>EXCLUDED.resume_token_hash";
    }

    private OffsetDateTime time(java.time.Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }
}
