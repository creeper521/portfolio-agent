package com.portfolio.agent.answer.context.adapter.postgres;

import com.portfolio.agent.answer.context.crypto.ContextEnvelopeCryptographyPort;
import com.portfolio.agent.answer.context.crypto.ResumeTokenHashPort;
import com.portfolio.agent.answer.context.domain.CompletionReceipt;
import com.portfolio.agent.answer.context.domain.ContextHandle;
import com.portfolio.agent.answer.context.domain.ConversationContextType;
import com.portfolio.agent.answer.context.domain.ConversationContinuationStatus;
import com.portfolio.agent.answer.context.domain.ConversationId;
import com.portfolio.agent.answer.context.domain.RequestFingerprint;
import com.portfolio.agent.answer.context.domain.ResumeToken;
import com.portfolio.agent.answer.context.gateway.RequestReceiptStore;
import com.portfolio.agent.answer.context.service.ConversationContextCapacityPolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL request receipt store with a short producer lease and encrypted completion state. */
public final class JdbcRequestReceiptStore implements RequestReceiptStore {
    private static final String RECEIPT_SCHEMA = "p3-receipt-v1";
    private static final Duration LEASE = Duration.ofSeconds(30);
    private static final Duration RECEIPT_TTL = Duration.ofMinutes(2);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactions;
    private final ContextEnvelopeCryptographyPort cryptography;
    private final ResumeTokenHashPort tokenHash;
    private final ConversationContextCapacityPolicy capacityPolicy;
    private final String schema;

    public JdbcRequestReceiptStore(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactions,
            ContextEnvelopeCryptographyPort cryptography,
            ResumeTokenHashPort tokenHash,
            ConversationContextCapacityPolicy capacityPolicy,
            String schema) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.cryptography = Objects.requireNonNull(cryptography, "cryptography");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        this.capacityPolicy = Objects.requireNonNull(capacityPolicy, "capacityPolicy");
        if (schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("invalid Context schema");
        }
        this.schema = schema;
    }

    @Override
    public ClaimResult claim(
            UUID requestToken,
            ConversationId conversationId,
            ResumeToken resumeToken,
            RequestFingerprint fingerprint,
            ContextHandle parentContextHandle,
            Instant now) {
        Objects.requireNonNull(requestToken, "requestToken");
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(resumeToken, "resumeToken");
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(now, "now");
        return transactions.execute(status -> claimInTransaction(
                requestToken, conversationId, resumeToken, fingerprint, parentContextHandle, now));
    }

    @Override
    public void complete(UUID requestToken, UUID leaseId, CompletionReceipt receipt, Instant now) {
        Objects.requireNonNull(requestToken, "requestToken");
        Objects.requireNonNull(receipt, "receipt");
        Objects.requireNonNull(now, "now");
        transactions.executeWithoutResult(status -> completeInTransaction(requestToken, leaseId, receipt, now));
    }

    @Override
    public Optional<CompletionReceipt> findCompleted(UUID requestToken, Instant now) {
        Objects.requireNonNull(requestToken, "requestToken");
        Objects.requireNonNull(now, "now");
        return transactions.execute(status -> jdbcTemplate.query(
                "SELECT request_token, conversation_id, request_fingerprint, status, lease_id, lease_expires_at, "
                        + "completion_key_id, completion_nonce, completion_ciphertext FROM "
                        + table("conversation_request_receipt")
                        + " WHERE request_token = ? AND status = 'COMPLETED' AND expires_at > ?",
                this::mapRow, requestToken, timestamp(now)).stream()
                .map(this::mapCompleted)
                .flatMap(Optional::stream)
                .findFirst());
    }

    private ClaimResult claimInTransaction(
            UUID requestToken, ConversationId conversationId, ResumeToken resumeToken,
            RequestFingerprint fingerprint, ContextHandle parentContextHandle, Instant now) {
        ensureSession(conversationId, resumeToken, now);
        if (!authorized(conversationId, resumeToken)) {
            return ClaimResult.conflict();
        }
        ReceiptRow existing = jdbcTemplate.query(
                "SELECT request_token, conversation_id, request_fingerprint, status, lease_id, lease_expires_at, "
                        + "completion_key_id, completion_nonce, completion_ciphertext, expires_at FROM "
                        + table("conversation_request_receipt") + " WHERE request_token = ? FOR UPDATE",
                this::mapRow, requestToken).stream().findFirst().orElse(null);
        byte[] fingerprintBytes = fingerprintBytes(fingerprint);
        if (existing == null) {
            UUID leaseId = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO " + table("conversation_request_receipt") + " ("
                            + "request_token, conversation_id, request_fingerprint, parent_context_handle, status, "
                            + "lease_id, lease_expires_at, created_at, updated_at, expires_at"
                            + ") VALUES (?, ?, ?, ?, 'IN_PROGRESS', ?, ?, ?, ?, ?)",
                    requestToken, conversationId.asUuid(), fingerprintBytes,
                    parentContextHandle == null ? null : parentContextHandle.asBase64Url(),
                    leaseId, timestamp(now.plus(LEASE)), timestamp(now), timestamp(now),
                    timestamp(now.plus(RECEIPT_TTL)));
            return ClaimResult.claimed(leaseId);
        }
        if (!existing.conversationId.equals(conversationId)
                || !MessageDigest.isEqual(existing.fingerprint, fingerprintBytes)) {
            return ClaimResult.conflict();
        }
        if ("COMPLETED".equals(existing.status)) {
            return mapCompleted(existing).map(ClaimResult::completed).orElse(ClaimResult.conflict());
        }
        if (existing.leaseExpiresAt != null && now.isBefore(existing.leaseExpiresAt)) {
            return ClaimResult.inProgress(Duration.between(now, existing.leaseExpiresAt));
        }
        UUID leaseId = UUID.randomUUID();
        jdbcTemplate.update("UPDATE " + table("conversation_request_receipt")
                        + " SET lease_id = ?, lease_expires_at = ?, updated_at = ?, expires_at = ? "
                        + "WHERE request_token = ? AND status = 'IN_PROGRESS'",
                leaseId, timestamp(now.plus(LEASE)), timestamp(now), timestamp(now.plus(RECEIPT_TTL)), requestToken);
        return ClaimResult.claimed(leaseId);
    }

    private void completeInTransaction(
            UUID requestToken, UUID leaseId, CompletionReceipt receipt, Instant now) {
        if (leaseId == null) {
            throw new IllegalStateException("request receipt lease is unavailable");
        }
        ReceiptRow row = jdbcTemplate.query(
                "SELECT request_token, conversation_id, request_fingerprint, status, lease_id, lease_expires_at, "
                        + "completion_key_id, completion_nonce, completion_ciphertext, expires_at FROM "
                        + table("conversation_request_receipt") + " WHERE request_token = ? FOR UPDATE",
                this::mapRow, requestToken).stream().findFirst().orElse(null);
        if (row == null || !"IN_PROGRESS".equals(row.status)
                || !leaseId.equals(row.leaseId)
                || !row.conversationId.equals(receipt.getConversationId())
                || !MessageDigest.isEqual(row.fingerprint, fingerprintBytes(receipt.getFingerprint()))) {
            throw new IllegalStateException("request receipt lease is unavailable");
        }
        ContextEnvelopeCryptographyPort.SealedContext sealed = cryptography.seal(
                receipt.getConversationId(), receiptHandle(requestToken),
                ConversationContextType.RECENT_SEMANTIC_TASK, RECEIPT_SCHEMA,
                encode(receipt));
        jdbcTemplate.update("UPDATE " + table("conversation_request_receipt") + " SET status = 'COMPLETED', "
                        + "lease_id = NULL, lease_expires_at = NULL, completion_key_id = ?, completion_nonce = ?, "
                        + "completion_ciphertext = ?, updated_at = ? WHERE request_token = ?",
                sealed.getKeyId(), sealed.getNonce(), sealed.getCiphertext(), timestamp(now), requestToken);
    }

    private Optional<CompletionReceipt> mapCompleted(ResultSet rs, int rowNum) throws SQLException {
        UUID requestToken = (UUID) rs.getObject("request_token");
        ConversationId conversationId = ConversationId.parse(rs.getObject("conversation_id").toString());
        byte[] fingerprint = rs.getBytes("request_fingerprint");
        return openReceipt(requestToken, conversationId, fingerprint,
                rs.getString("completion_key_id"), rs.getBytes("completion_nonce"),
                rs.getBytes("completion_ciphertext"));
    }

    private Optional<CompletionReceipt> mapCompleted(ReceiptRow row) {
        return openReceipt(row.requestToken, row.conversationId, row.fingerprint,
                row.completionKeyId, row.completionNonce, row.completionCiphertext);
    }

    private Optional<CompletionReceipt> openReceipt(
            UUID requestToken, ConversationId conversationId, byte[] fingerprint,
            String keyId, byte[] nonce, byte[] ciphertext) {
        if (keyId == null || nonce == null || ciphertext == null) return Optional.empty();
        byte[] plain = cryptography.open(conversationId, receiptHandle(requestToken),
                ConversationContextType.RECENT_SEMANTIC_TASK, RECEIPT_SCHEMA,
                new ContextEnvelopeCryptographyPort.SealedContext(keyId, nonce, ciphertext));
        CompletionReceipt receipt = decode(plain);
        if (!receipt.getRequestToken().equals(requestToken)
                || !receipt.getConversationId().equals(conversationId)
                || !MessageDigest.isEqual(fingerprint, fingerprintBytes(receipt.getFingerprint()))) {
            throw new IllegalStateException("request receipt integrity check failed");
        }
        return Optional.of(receipt);
    }

    private ReceiptRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        ReceiptRow row = new ReceiptRow();
        row.requestToken = (UUID) rs.getObject("request_token");
        row.conversationId = ConversationId.parse(rs.getObject("conversation_id").toString());
        row.fingerprint = rs.getBytes("request_fingerprint");
        row.status = rs.getString("status");
        row.leaseId = (UUID) rs.getObject("lease_id");
        Timestamp leaseExpires = rs.getTimestamp("lease_expires_at");
        row.leaseExpiresAt = leaseExpires == null ? null : instant(leaseExpires);
        row.completionKeyId = rs.getString("completion_key_id");
        row.completionNonce = rs.getBytes("completion_nonce");
        row.completionCiphertext = rs.getBytes("completion_ciphertext");
        return row;
    }

    private void ensureSession(ConversationId conversationId, ResumeToken resumeToken, Instant now) {
        SessionRow existing = jdbcTemplate.query(
                "SELECT resume_token_hash, token_key_id FROM " + table("conversation_session")
                        + " WHERE conversation_id = ?", (rs, rowNum) -> new SessionRow(
                        rs.getBytes("resume_token_hash"), rs.getString("token_key_id")), conversationId.asUuid())
                .stream().findFirst().orElse(null);
        if (existing != null) return;
        ResumeTokenHashPort.HashedToken hashed = tokenHash.hash(resumeToken);
        jdbcTemplate.update("INSERT INTO " + table("conversation_session") + " ("
                        + "conversation_id, resume_token_hash, token_key_id, created_at, last_accessed_at, "
                        + "idle_expires_at, absolute_expires_at, context_count, payload_bytes, revision"
                        + ") VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0, 0)",
                conversationId.asUuid(), hashed.getDigest(), hashed.getKeyId(), timestamp(now), timestamp(now),
                timestamp(capacityPolicy.idleExpiresAt(now)), timestamp(capacityPolicy.absoluteExpiresAt(now)));
    }

    private boolean authorized(ConversationId conversationId, ResumeToken resumeToken) {
        return jdbcTemplate.query(
                "SELECT resume_token_hash, token_key_id FROM " + table("conversation_session")
                        + " WHERE conversation_id = ?", (rs, rowNum) -> tokenHash.matches(resumeToken,
                        new ResumeTokenHashPort.HashedToken(
                                rs.getString("token_key_id"), rs.getBytes("resume_token_hash"))),
                conversationId.asUuid()).stream().findFirst().orElse(false);
    }

    private byte[] encode(CompletionReceipt receipt) {
        String context = receipt.getContextHandle().map(ContextHandle::asBase64Url).orElse("");
        return String.join("|", receipt.getRequestToken().toString(), receipt.getConversationId().toString(),
                receipt.getFingerprint().value(), context, receipt.getContinuationStatus().name(),
                Long.toString(receipt.getCompletedAt().toEpochMilli())).getBytes(StandardCharsets.UTF_8);
    }

    private CompletionReceipt decode(byte[] encoded) {
        String[] fields = new String(encoded, StandardCharsets.UTF_8).split("\\|", -1);
        if (fields.length != 6) throw new IllegalStateException("request receipt payload is invalid");
        return new CompletionReceipt(UUID.fromString(fields[0]), ConversationId.parse(fields[1]),
                new RequestFingerprint(fields[2]), fields[3].isBlank() ? null : ContextHandle.fromBase64Url(fields[3]),
                ConversationContinuationStatus.valueOf(fields[4]), Instant.ofEpochMilli(Long.parseLong(fields[5])));
    }

    private static byte[] fingerprintBytes(RequestFingerprint fingerprint) {
        try {
            byte[] value = Base64.getUrlDecoder().decode(fingerprint.value());
            if (value.length != 32) throw new IllegalArgumentException("fingerprint must be SHA-256");
            return value;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("fingerprint must be SHA-256 base64url", exception);
        }
    }

    private static ContextHandle receiptHandle(UUID requestToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(requestToken.toString().getBytes(StandardCharsets.UTF_8));
            return ContextHandle.fromBase64Url(Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(java.util.Arrays.copyOf(digest, ContextHandle.BYTE_LENGTH)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String table(String name) { return schema + "." + name; }
    private static Timestamp timestamp(Instant instant) { return Timestamp.from(instant); }
    private static Instant instant(Timestamp timestamp) { return timestamp.toInstant(); }

    private static final class SessionRow {
        private final byte[] hash;
        private final String keyId;
        private SessionRow(byte[] hash, String keyId) { this.hash = hash; this.keyId = keyId; }
    }

    private static final class ReceiptRow {
        private UUID requestToken;
        private ConversationId conversationId;
        private byte[] fingerprint;
        private String status;
        private UUID leaseId;
        private Instant leaseExpiresAt;
        private String completionKeyId;
        private byte[] completionNonce;
        private byte[] completionCiphertext;
    }
}
