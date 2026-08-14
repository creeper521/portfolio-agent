package com.portfolio.agent.answer.context.adapter.postgres;

import com.portfolio.agent.answer.context.adapter.memory.ContextCapacityExceededException;
import com.portfolio.agent.answer.context.codec.ConversationContextCodecRegistry;
import com.portfolio.agent.answer.context.crypto.ContextEnvelopeCryptographyPort;
import com.portfolio.agent.answer.context.crypto.ResumeTokenHashPort;
import com.portfolio.agent.answer.context.domain.ContextHandle;
import com.portfolio.agent.answer.context.domain.ContextSlot;
import com.portfolio.agent.answer.context.domain.ConversationContextEntry;
import com.portfolio.agent.answer.context.domain.ConversationContextMutation;
import com.portfolio.agent.answer.context.domain.ConversationContextType;
import com.portfolio.agent.answer.context.domain.ConversationContextValue;
import com.portfolio.agent.answer.context.domain.ConversationId;
import com.portfolio.agent.answer.context.domain.RecommendationContext;
import com.portfolio.agent.answer.context.domain.RecentSemanticTaskContext;
import com.portfolio.agent.answer.context.domain.ResumeToken;
import com.portfolio.agent.answer.context.gateway.ConversationBusinessContextStore;
import com.portfolio.agent.answer.context.service.ConversationContextCapacityPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** PostgreSQL implementation with encrypted typed payloads and Active CAS. */
public final class JdbcConversationBusinessContextStore implements ConversationBusinessContextStore {
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactions;
    private final ConversationContextCodecRegistry codecRegistry;
    private final ContextEnvelopeCryptographyPort cryptography;
    private final ResumeTokenHashPort tokenHash;
    private final ConversationContextCapacityPolicy capacityPolicy;
    private final String schema;

    public JdbcConversationBusinessContextStore(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactions,
            ConversationContextCodecRegistry codecRegistry,
            ContextEnvelopeCryptographyPort cryptography,
            ResumeTokenHashPort tokenHash,
            ConversationContextCapacityPolicy capacityPolicy,
            String schema) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.codecRegistry = Objects.requireNonNull(codecRegistry, "codecRegistry");
        this.cryptography = Objects.requireNonNull(cryptography, "cryptography");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.capacityPolicy = Objects.requireNonNull(capacityPolicy, "capacityPolicy");
        if (schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("invalid Context schema");
        }
        this.schema = schema;
    }

    @Override
    public void open(ConversationId conversationId, ResumeToken resumeToken, Instant now) {
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(resumeToken, "resumeToken");
        Objects.requireNonNull(now, "now");
        transactions.executeWithoutResult(status -> {
            ensureSession(conversationId, resumeToken, now);
            if (authorizedSession(conversationId, resumeToken) == null) {
                throw new IllegalArgumentException("Context session is not authorized");
            }
        });
    }

    @Override
    public void rotateResumeToken(
            ConversationId conversationId, ResumeToken replacement, Instant now) {
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(replacement, "replacement");
        Objects.requireNonNull(now, "now");
        transactions.executeWithoutResult(status -> {
            if (session(conversationId) == null) {
                throw new IllegalArgumentException("Context session is unavailable");
            }
            ResumeTokenHashPort.HashedToken hashed = tokenHash.hash(replacement);
            int updated = jdbcTemplate.update("UPDATE " + table("conversation_session")
                            + " SET resume_token_hash = ?, token_key_id = ?, last_accessed_at = ?"
                            + " WHERE conversation_id = ?",
                    hashed.getDigest(), hashed.getKeyId(), timestamp(now), conversationId.asUuid());
            if (updated != 1) {
                throw new IllegalArgumentException("Context session is unavailable");
            }
        });
    }

    @Override
    public SaveResult save(
            ConversationId conversationId,
            ResumeToken resumeToken,
            ConversationContextMutation mutation,
            Instant now) {
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(resumeToken, "resumeToken");
        Objects.requireNonNull(mutation, "mutation");
        Objects.requireNonNull(now, "now");
        capacityPolicy.requirePayloadSize(mutation.getPayloadBytes());
        Encoded sealed = encodeAndSeal(conversationId, mutation);
        return transactions.execute(status -> saveInTransaction(
                conversationId, resumeToken, mutation, sealed, now));
    }

    @Override
    public Optional<ConversationContextEntry> resolve(
            ConversationId conversationId,
            ResumeToken resumeToken,
            ContextHandle contextHandle,
            Instant now) {
        return transactions.execute(status -> resolveInTransaction(
                conversationId, resumeToken, contextHandle, now));
    }

    @Override
    public LookupResult lookup(
            ConversationId conversationId,
            ResumeToken resumeToken,
            ContextHandle contextHandle,
            Instant now) {
        return transactions.execute(status -> lookupInTransaction(
                conversationId, resumeToken, contextHandle, now));
    }

    @Override
    public List<ConversationContextEntry> list(
            ConversationId conversationId, ResumeToken resumeToken, Instant now) {
        return transactions.execute(status -> listInTransaction(conversationId, resumeToken, now));
    }

    @Override
    public Optional<ActiveContext> active(
            ConversationId conversationId,
            ResumeToken resumeToken,
            ContextSlot slot,
            Instant now) {
        return transactions.execute(status -> activeInTransaction(
                conversationId, resumeToken, slot, now));
    }

    @Override
    public Optional<ConversationId> findConversation(ResumeToken resumeToken) {
        return transactions.execute(status -> {
            List<ConversationId> conversations = jdbcTemplate.query(
                    "SELECT conversation_id, resume_token_hash, token_key_id FROM "
                            + table("conversation_session"),
                    (rs, rowNum) -> tokenHash.matches(resumeToken,
                            new ResumeTokenHashPort.HashedToken(
                                    rs.getString("token_key_id"), rs.getBytes("resume_token_hash")))
                            ? ConversationId.parse(rs.getObject("conversation_id").toString()) : null);
            return conversations.stream().filter(Objects::nonNull).findFirst();
        });
    }

    @Override
    public Optional<ConversationContextEntry> resolve(
            ResumeToken resumeToken, ContextHandle contextHandle, Instant now) {
        return findConversation(resumeToken)
                .flatMap(conversationId -> resolve(conversationId, resumeToken, contextHandle, now));
    }

    @Override
    public void clear(ConversationId conversationId, ResumeToken resumeToken) {
        transactions.executeWithoutResult(status -> {
            SessionRow session = session(conversationId);
            if (session != null && tokenHash.matches(resumeToken,
                    new ResumeTokenHashPort.HashedToken(
                            session.tokenKeyId, session.resumeTokenHash))) {
                jdbcTemplate.update("DELETE FROM " + table("conversation_session")
                        + " WHERE conversation_id = ?", conversationId.asUuid());
            }
        });
    }

    @Override
    public void clear(ResumeToken resumeToken) {
        findConversation(resumeToken).ifPresent(conversationId -> clear(conversationId, resumeToken));
    }

    private SaveResult saveInTransaction(
            ConversationId conversationId,
            ResumeToken resumeToken,
            ConversationContextMutation mutation,
            Encoded sealed,
            Instant now) {
        ensureSession(conversationId, resumeToken, now);
        SessionRow session = authorizedSession(conversationId, resumeToken);
        if (mutation.getParentContextHandle() != null && !existsContext(
                conversationId, mutation.getParentContextHandle(), now)) {
            throw new IllegalArgumentException("parent Context is unavailable");
        }
        pruneForInsert(conversationId, mutation.getContextHandle(), now);
        jdbcTemplate.update("INSERT INTO " + table("conversation_context") + " ("
                        + "conversation_id, context_handle, context_type, parent_context_handle, "
                        + "source_task_id, content_version_binding, schema_version, encryption_key_id, nonce, "
                        + "typed_context_ciphertext, payload_bytes, created_at, last_accessed_at, expires_at, absolute_expires_at"
                        + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                conversationId.asUuid(), mutation.getContextHandle().asBase64Url(),
                mutation.getValue().getType().name(), nullableHandle(mutation.getParentContextHandle()),
                mutation.getSourceTaskId(), contentVersion(mutation.getValue()), sealed.schemaVersion,
                sealed.sealedContext.getKeyId(), sealed.sealedContext.getNonce(),
                sealed.sealedContext.getCiphertext(), mutation.getPayloadBytes(), timestamp(now), timestamp(now),
                timestamp(capacityPolicy.idleExpiresAt(now)), timestamp(capacityPolicy.absoluteExpiresAt(now)));

        boolean activeAdvanced = false;
        long activeRevision = currentActiveRevision(conversationId, mutation.getActiveSlot());
        if (mutation.getActiveSlot() != null) {
            long expected = mutation.getExpectedActiveRevision() == null
                    ? 0L : mutation.getExpectedActiveRevision();
            int affected;
            if (expected == 0L) {
                affected = jdbcTemplate.update("INSERT INTO " + table("conversation_active_context")
                                + " (conversation_id, active_slot, context_handle, revision, updated_at) VALUES (?, ?, ?, ?, ?) "
                                + "ON CONFLICT (conversation_id, active_slot) DO UPDATE SET "
                                + "context_handle = EXCLUDED.context_handle, revision = "
                                + table("conversation_active_context") + ".revision + 1, updated_at = EXCLUDED.updated_at "
                                + "WHERE " + table("conversation_active_context") + ".revision = 0",
                        conversationId.asUuid(), mutation.getActiveSlot().name(),
                        mutation.getContextHandle().asBase64Url(), 1L, timestamp(now));
            } else {
                affected = jdbcTemplate.update("UPDATE " + table("conversation_active_context")
                                + " SET context_handle = ?, revision = revision + 1, updated_at = ?"
                                + " WHERE conversation_id = ? AND active_slot = ? AND revision = ?",
                        mutation.getContextHandle().asBase64Url(), timestamp(now), conversationId.asUuid(),
                        mutation.getActiveSlot().name(), expected);
            }
            activeAdvanced = affected == 1;
            activeRevision = activeAdvanced ? expected + 1
                    : currentActiveRevision(conversationId, mutation.getActiveSlot());
        }
        refreshSessionCounters(conversationId, now);
        ConversationContextEntry entry = loadEntry(
                conversationId, mutation.getContextHandle(), mutation.getValue().getType(), sealed.schemaVersion,
                now, false);
        return new SaveResult(entry, activeAdvanced, activeRevision);
    }

    private Optional<ConversationContextEntry> resolveInTransaction(
            ConversationId conversationId,
            ResumeToken resumeToken,
            ContextHandle contextHandle,
            Instant now) {
        SessionRow session = authorizedSession(conversationId, resumeToken);
        if (session == null) {
            return Optional.empty();
        }
        ContextRow row = contextRow(conversationId, contextHandle);
        if (row == null || !now.isBefore(row.expiresAt) || !now.isBefore(row.absoluteExpiresAt)) {
            return Optional.empty();
        }
        Instant idleExpiresAt = capacityPolicy.idleExpiresAt(now);
        if (idleExpiresAt.isAfter(row.absoluteExpiresAt)) {
            idleExpiresAt = row.absoluteExpiresAt;
        }
        jdbcTemplate.update("UPDATE " + table("conversation_context")
                        + " SET last_accessed_at = ?, expires_at = ? WHERE conversation_id = ? AND context_handle = ?",
                timestamp(now), timestamp(idleExpiresAt), conversationId.asUuid(), contextHandle.asBase64Url());
        jdbcTemplate.update("UPDATE " + table("conversation_session")
                        + " SET last_accessed_at = ? WHERE conversation_id = ?",
                timestamp(now), conversationId.asUuid());
        row.lastAccessedAt = now;
        row.expiresAt = idleExpiresAt;
        return Optional.of(decode(row));
    }

    private LookupResult lookupInTransaction(
            ConversationId conversationId,
            ResumeToken resumeToken,
            ContextHandle contextHandle,
            Instant now) {
        if (authorizedSession(conversationId, resumeToken) == null) {
            return LookupResult.notFound();
        }
        ContextRow row = contextRow(conversationId, contextHandle);
        if (row == null) {
            return LookupResult.notFound();
        }
        if (!now.isBefore(row.expiresAt) || !now.isBefore(row.absoluteExpiresAt)) {
            return LookupResult.expired();
        }
        return resolveInTransaction(conversationId, resumeToken, contextHandle, now)
                .map(LookupResult::found).orElseGet(LookupResult::notFound);
    }

    private List<ConversationContextEntry> listInTransaction(
            ConversationId conversationId, ResumeToken resumeToken, Instant now) {
        if (authorizedSession(conversationId, resumeToken) == null) {
            return List.of();
        }
        List<ContextRow> rows = jdbcTemplate.query(
                "SELECT conversation_id, context_handle, context_type, parent_context_handle, source_task_id, "
                        + "schema_version, encryption_key_id, nonce, typed_context_ciphertext, payload_bytes, "
                        + "created_at, last_accessed_at, expires_at, absolute_expires_at FROM "
                        + table("conversation_context")
                        + " WHERE conversation_id = ? AND expires_at > ? AND absolute_expires_at > ? "
                        + "ORDER BY created_at DESC, context_handle", this::mapContextRow,
                conversationId.asUuid(), timestamp(now), timestamp(now));
        List<ConversationContextEntry> entries = new ArrayList<>();
        for (ContextRow row : rows) {
            entries.add(decode(row));
        }
        return List.copyOf(entries);
    }

    private Optional<ActiveContext> activeInTransaction(
            ConversationId conversationId, ResumeToken resumeToken, ContextSlot slot, Instant now) {
        if (authorizedSession(conversationId, resumeToken) == null) {
            return Optional.empty();
        }
        List<ActiveContext> values = jdbcTemplate.query(
                "SELECT active_slot, context_handle, revision FROM " + table("conversation_active_context")
                        + " a JOIN " + table("conversation_context")
                        + " c USING (conversation_id, context_handle) WHERE a.conversation_id = ? "
                        + "AND a.active_slot = ? AND c.expires_at > ? AND c.absolute_expires_at > ?",
                (rs, rowNum) -> new ActiveContext(
                        ContextSlot.valueOf(rs.getString("active_slot")),
                        ContextHandle.fromBase64Url(rs.getString("context_handle")),
                        rs.getLong("revision")),
                conversationId.asUuid(), slot.name(), timestamp(now), timestamp(now));
        return values.stream().findFirst();
    }

    private void ensureSession(ConversationId conversationId, ResumeToken resumeToken, Instant now) {
        SessionRow existing = session(conversationId);
        if (existing != null) {
            if (!tokenHash.matches(resumeToken,
                    new ResumeTokenHashPort.HashedToken(existing.tokenKeyId, existing.resumeTokenHash))) {
                throw new IllegalArgumentException("Context session is not authorized");
            }
            return;
        }
        ResumeTokenHashPort.HashedToken hashed = tokenHash.hash(resumeToken);
        jdbcTemplate.update("INSERT INTO " + table("conversation_session") + " ("
                        + "conversation_id, resume_token_hash, token_key_id, created_at, last_accessed_at, "
                        + "idle_expires_at, absolute_expires_at, context_count, payload_bytes, revision"
                        + ") VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0, 0)",
                conversationId.asUuid(), hashed.getDigest(), hashed.getKeyId(), timestamp(now), timestamp(now),
                timestamp(capacityPolicy.idleExpiresAt(now)), timestamp(capacityPolicy.absoluteExpiresAt(now)));
    }

    private SessionRow authorizedSession(ConversationId conversationId, ResumeToken resumeToken) {
        SessionRow session = session(conversationId);
        if (session == null || !tokenHash.matches(resumeToken,
                new ResumeTokenHashPort.HashedToken(session.tokenKeyId, session.resumeTokenHash))) {
            return null;
        }
        return session;
    }

    private SessionRow session(ConversationId conversationId) {
        List<SessionRow> sessions = jdbcTemplate.query(
                "SELECT resume_token_hash, token_key_id FROM " + table("conversation_session")
                        + " WHERE conversation_id = ?", (rs, rowNum) -> new SessionRow(
                        rs.getBytes("resume_token_hash"), rs.getString("token_key_id")),
                conversationId.asUuid());
        return sessions.stream().findFirst().orElse(null);
    }

    private boolean existsContext(ConversationId conversationId, ContextHandle handle, Instant now) {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM " + table("conversation_context")
                        + " WHERE conversation_id = ? AND context_handle = ? AND expires_at > ? AND absolute_expires_at > ?",
                Integer.class, conversationId.asUuid(), handle.asBase64Url(), timestamp(now), timestamp(now));
        return count != null && count == 1;
    }

    private void pruneForInsert(ConversationId conversationId, ContextHandle newHandle, Instant now) {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM " + table("conversation_context")
                        + " WHERE conversation_id = ?", Integer.class, conversationId.asUuid());
        if (count == null || count < capacityPolicy.getMaxContexts()) {
            return;
        }
        int required = count - capacityPolicy.getMaxContexts() + 1;
        List<String> candidates = jdbcTemplate.query(
                "SELECT c.context_handle FROM " + table("conversation_context") + " c "
                        + "WHERE c.conversation_id = ? "
                        + "AND NOT EXISTS (SELECT 1 FROM " + table("conversation_active_context")
                        + " a WHERE a.conversation_id = c.conversation_id AND a.context_handle = c.context_handle) "
                        + "AND NOT EXISTS (SELECT 1 FROM " + table("conversation_context")
                        + " child WHERE child.conversation_id = c.conversation_id "
                        + "AND child.parent_context_handle = c.context_handle) "
                        + "ORDER BY CASE WHEN c.context_type = 'RECOMMENDATION' THEN 1 ELSE 0 END, "
                        + "c.created_at, c.context_handle LIMIT ?", (rs, rowNum) -> rs.getString("context_handle"),
                conversationId.asUuid(), required);
        if (candidates.size() < required) {
            throw new ContextCapacityExceededException();
        }
        for (String candidate : candidates) {
            jdbcTemplate.update("DELETE FROM " + table("conversation_context")
                    + " WHERE conversation_id = ? AND context_handle = ?",
                    conversationId.asUuid(), candidate);
        }
    }

    private void refreshSessionCounters(ConversationId conversationId, Instant now) {
        jdbcTemplate.update("UPDATE " + table("conversation_session") + " s SET "
                        + "context_count = (SELECT count(*) FROM " + table("conversation_context")
                        + " c WHERE c.conversation_id = s.conversation_id), "
                        + "payload_bytes = COALESCE((SELECT sum(payload_bytes) FROM " + table("conversation_context")
                        + " c WHERE c.conversation_id = s.conversation_id), 0), last_accessed_at = ? "
                        + "WHERE s.conversation_id = ?", timestamp(now), conversationId.asUuid());
    }

    private long currentActiveRevision(ConversationId conversationId, ContextSlot slot) {
        if (slot == null) {
            return 0L;
        }
        List<Long> revisions = jdbcTemplate.query("SELECT revision FROM " + table("conversation_active_context")
                        + " WHERE conversation_id = ? AND active_slot = ?",
                (rs, rowNum) -> rs.getLong("revision"), conversationId.asUuid(), slot.name());
        return revisions.stream().findFirst().orElse(0L);
    }

    private Encoded encodeAndSeal(
            ConversationId conversationId, ConversationContextMutation mutation) {
        Object context = mutation.getValue().getType() == ConversationContextType.RECENT_SEMANTIC_TASK
                ? mutation.getValue().getRecentSemanticTaskContext()
                : mutation.getValue().getRecommendationContext();
        ConversationContextCodecRegistry.EncodedContext encoded = codecRegistry.encode(
                mutation.getValue().getType(), context);
        ContextEnvelopeCryptographyPort.SealedContext sealed = cryptography.seal(
                conversationId, mutation.getContextHandle(), mutation.getValue().getType(),
                encoded.getSchemaVersion(), encoded.getPayload());
        return new Encoded(encoded.getSchemaVersion(), sealed);
    }

    private ConversationContextEntry loadEntry(
            ConversationId conversationId,
            ContextHandle contextHandle,
            ConversationContextType type,
            String schemaVersion,
            Instant now,
            boolean requireCurrent) {
        ContextRow row = contextRow(conversationId, contextHandle);
        if (row == null || (requireCurrent && (!now.isBefore(row.expiresAt)
                || !now.isBefore(row.absoluteExpiresAt))) || row.type != type) {
            throw new IllegalStateException("Context was not persisted");
        }
        if (!row.schemaVersion.equals(schemaVersion)) {
            throw new IllegalStateException("Context schema changed during persistence");
        }
        return decode(row);
    }

    private ContextRow contextRow(ConversationId conversationId, ContextHandle contextHandle) {
        List<ContextRow> rows = jdbcTemplate.query(
                "SELECT conversation_id, context_handle, context_type, parent_context_handle, source_task_id, "
                        + "schema_version, encryption_key_id, nonce, typed_context_ciphertext, payload_bytes, "
                        + "created_at, last_accessed_at, expires_at, absolute_expires_at FROM "
                        + table("conversation_context") + " WHERE conversation_id = ? AND context_handle = ?",
                this::mapContextRow, conversationId.asUuid(), contextHandle.asBase64Url());
        return rows.stream().findFirst().orElse(null);
    }

    private ContextRow mapContextRow(ResultSet rs, int rowNum) throws SQLException {
        ContextRow row = new ContextRow();
        row.conversationId = ConversationId.parse(rs.getObject("conversation_id").toString());
        row.contextHandle = ContextHandle.fromBase64Url(rs.getString("context_handle"));
        row.type = ConversationContextType.valueOf(rs.getString("context_type"));
        String parent = rs.getString("parent_context_handle");
        row.parentContextHandle = parent == null ? null : ContextHandle.fromBase64Url(parent);
        row.sourceTaskId = rs.getString("source_task_id");
        row.schemaVersion = rs.getString("schema_version");
        row.keyId = rs.getString("encryption_key_id");
        row.nonce = rs.getBytes("nonce");
        row.ciphertext = rs.getBytes("typed_context_ciphertext");
        row.payloadBytes = rs.getInt("payload_bytes");
        row.createdAt = instant(rs.getTimestamp("created_at"));
        row.lastAccessedAt = instant(rs.getTimestamp("last_accessed_at"));
        row.expiresAt = instant(rs.getTimestamp("expires_at"));
        row.absoluteExpiresAt = instant(rs.getTimestamp("absolute_expires_at"));
        return row;
    }

    private ConversationContextEntry decode(ContextRow row) {
        byte[] payload = cryptography.open(
                row.conversationId, row.contextHandle, row.type, row.schemaVersion,
                new ContextEnvelopeCryptographyPort.SealedContext(row.keyId, row.nonce, row.ciphertext));
        Object context = codecRegistry.decode(new ConversationContextCodecRegistry.EncodedContext(
                row.type, row.schemaVersion, payload));
        ConversationContextValue value = row.type == ConversationContextType.RECENT_SEMANTIC_TASK
                ? ConversationContextValue.recentSemanticTask((RecentSemanticTaskContext) context)
                : ConversationContextValue.recommendation((RecommendationContext) context);
        return new ConversationContextEntry(
                row.conversationId, row.contextHandle, value, row.parentContextHandle, row.sourceTaskId,
                row.payloadBytes, row.createdAt, row.lastAccessedAt, row.expiresAt, row.absoluteExpiresAt);
    }

    private String contentVersion(ConversationContextValue value) {
        return value.getType() == ConversationContextType.RECENT_SEMANTIC_TASK
                ? value.getRecentSemanticTaskContext().getContentVersion()
                : value.getRecommendationContext().getAuthorizedScope().getContentVersion();
    }

    private String nullableHandle(ContextHandle handle) {
        return handle == null ? null : handle.asBase64Url();
    }

    private String table(String name) {
        return schema + "." + name;
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp.toInstant();
    }

    private static final class SessionRow {
        private final byte[] resumeTokenHash;
        private final String tokenKeyId;

        private SessionRow(byte[] resumeTokenHash, String tokenKeyId) {
            this.resumeTokenHash = resumeTokenHash;
            this.tokenKeyId = tokenKeyId;
        }
    }

    private static final class ContextRow {
        private ConversationId conversationId;
        private ContextHandle contextHandle;
        private ConversationContextType type;
        private ContextHandle parentContextHandle;
        private String sourceTaskId;
        private String schemaVersion;
        private String keyId;
        private byte[] nonce;
        private byte[] ciphertext;
        private int payloadBytes;
        private Instant createdAt;
        private Instant lastAccessedAt;
        private Instant expiresAt;
        private Instant absoluteExpiresAt;
    }

    private static final class Encoded {
        private final String schemaVersion;
        private final ContextEnvelopeCryptographyPort.SealedContext sealedContext;

        private Encoded(String schemaVersion, ContextEnvelopeCryptographyPort.SealedContext sealedContext) {
            this.schemaVersion = schemaVersion;
            this.sealedContext = sealedContext;
        }
    }
}
