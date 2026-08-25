package com.portfolio.agent.turn.continuation;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.security.SecureRandom;
import com.portfolio.agent.turn.execution.TurnDeadline;

/**
 * Authenticates existing sessions and creates deterministic tentative first-turn identity.
 *
 * <p>会话解析器：用 ResumeToken 哈希认证既有会话；无令牌的首轮请求签发
 * 随机令牌，并以请求 ID 派生确定性的临时会话 ID。支持多密钥轮换
 * （当前密钥 + 历史 secrets 依次尝试）。</p>
 */
public final class ConversationSessionResolver {
    private final ConversationSessionStore store;
    private final List<byte[]> secrets;
    private final Clock clock;
    private final Duration ttl;
    private final SecureRandom random;
    public ConversationSessionResolver(
            ConversationSessionStore store, byte[] secret, Clock clock, Duration ttl) {
        this(store, secret, List.of(), clock, ttl, new SecureRandom());
    }
    public ConversationSessionResolver(
            ConversationSessionStore store, byte[] currentSecret,
            List<byte[]> previousSecrets, Clock clock, Duration ttl) {
        this(store, currentSecret, previousSecrets, clock, ttl, new SecureRandom());
    }
    ConversationSessionResolver(
            ConversationSessionStore store, byte[] currentSecret,
            List<byte[]> previousSecrets, Clock clock, Duration ttl,
            SecureRandom random) {
        this.store = Objects.requireNonNull(store, "store");
        java.util.ArrayList<byte[]> configuredSecrets = new java.util.ArrayList<>();
        configuredSecrets.add(requireSecret(currentSecret));
        Objects.requireNonNull(previousSecrets, "previousSecrets")
                .forEach(value -> configuredSecrets.add(requireSecret(value)));
        this.secrets = List.copyOf(configuredSecrets);
        this.clock = Objects.requireNonNull(clock, "clock");
        if (ttl == null || ttl.isZero() || ttl.isNegative()) throw new IllegalArgumentException("ttl is invalid");
        this.ttl = ttl;
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * 解析会话：无令牌则签发临时身份，有令牌则按密钥集合认证。
     *
     * @param bearerToken 客户端持有的 ResumeToken 编码，可为 null
     * @return TENTATIVE（新签发令牌）、AUTHENTICATED（命中既有会话）
     *         或 INVALID（令牌无法认证）
     */
    public Resolution resolve(
            String bearerToken, UUID requestId, TurnDeadline deadline) {
        if (bearerToken == null) {
            byte[] tokenBytes = new byte[ResumeToken.BYTE_LENGTH];
            random.nextBytes(tokenBytes);
            ResumeToken token = ResumeToken.of(tokenBytes);
            String conversationId = UUID.nameUUIDFromBytes(
                    requestIdBytes(requestId)).toString();
            return new Resolution(
                    Status.TENTATIVE, conversationId,
                    hash(token), token, null);
        }
        ResumeToken token;
        try { token = ResumeToken.parse(bearerToken); }
        catch (IllegalArgumentException failure) { return Resolution.invalid(); }
        List<byte[]> hashes = secrets.stream().map(secret -> hash(token, secret)).toList();
        Optional<ConversationSessionStore.Session> found =
                store.find(hashes, clock.instant(), deadline);
        return found.map(session -> new Resolution(
                        Status.AUTHENTICATED, session.conversationId(),
                        session.tokenHash(), null, session))
                .orElseGet(Resolution::invalid);
    }

    /** 为临时身份构建待保存的会话行（TTL 从当前时刻起算）。 */
    public ConversationSessionStore.Session pendingSession(Resolution resolution) {
        if (resolution.status() != Status.TENTATIVE) return null;
        return new ConversationSessionStore.Session(
                resolution.conversationId(), resolution.tokenHash(),
                clock.instant(), clock.instant().plus(ttl));
    }
    /** 用当前密钥计算令牌哈希。 */
    private byte[] hash(ResumeToken token) { return hash(token, secrets.getFirst()); }
    /** 用指定密钥计算令牌哈希。 */
    private byte[] hash(ResumeToken token, byte[] secret) {
        return hmac(secret, token.copyBytes());
    }
    /** HmacSHA256 计算；算法不可用时抛出 IllegalStateException。 */
    private byte[] hmac(byte[] secret, byte[] value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(value);
        } catch (Exception failure) { throw new IllegalStateException("session HMAC unavailable", failure); }
    }

    /** 校验密钥至少 32 字节并克隆，避免外部持有引用。 */
    private byte[] requireSecret(byte[] value) {
        byte[] copy = Objects.requireNonNull(value, "secret").clone();
        if (copy.length < 32) throw new IllegalArgumentException("session secret is too short");
        return copy;
    }

    /** 把请求 UUID 转为 16 字节，用于派生确定性的临时会话 ID。 */
    private byte[] requestIdBytes(UUID requestId) {
        return ByteBuffer.allocate(16)
                .putLong(requestId.getMostSignificantBits())
                .putLong(requestId.getLeastSignificantBits()).array();
    }

    /** 解析结果：状态、会话 ID、令牌哈希、新签发令牌与命中的会话行。 */
    public record Resolution(
            Status status, String conversationId,
            byte[] tokenHash, ResumeToken issuedToken,
            ConversationSessionStore.Session session) {
        public Resolution {
            tokenHash = tokenHash == null ? null : tokenHash.clone();
        }
        @Override public byte[] tokenHash() { return tokenHash == null ? null : tokenHash.clone(); }
        static Resolution invalid() {
            return new Resolution(
                    Status.INVALID, null, null, null, null);
        }
    }
    /** 会话状态：临时（新签发）/已认证/无效。 */
    public enum Status { TENTATIVE, AUTHENTICATED, INVALID }
}
