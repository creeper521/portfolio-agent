package com.portfolio.agent.turn.continuation;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Authenticates existing sessions and creates deterministic tentative first-turn identity. */
public final class ConversationSessionResolver {
    private final ConversationSessionStore store;
    private final byte[] secret;
    private final Clock clock;
    private final Duration ttl;
    public ConversationSessionResolver(
            ConversationSessionStore store, byte[] secret, Clock clock, Duration ttl) {
        this.store = Objects.requireNonNull(store, "store");
        this.secret = Objects.requireNonNull(secret, "secret").clone();
        if (this.secret.length < 32) throw new IllegalArgumentException("session secret is too short");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (ttl == null || ttl.isZero() || ttl.isNegative()) throw new IllegalArgumentException("ttl is invalid");
        this.ttl = ttl;
    }

    public Resolution resolve(String bearerToken, UUID requestId) {
        if (bearerToken == null) {
            ResumeToken token = ResumeToken.of(hmac("token:" + requestId));
            String conversationId = UUID.nameUUIDFromBytes(
                    hmac("conversation:" + requestId)).toString();
            return new Resolution(Status.TENTATIVE, conversationId, hash(token), token);
        }
        ResumeToken token;
        try { token = ResumeToken.parse(bearerToken); }
        catch (IllegalArgumentException failure) { return Resolution.invalid(); }
        byte[] hash = hash(token);
        return store.find(hash, clock.instant())
                .map(value -> new Resolution(Status.AUTHENTICATED,
                        value.conversationId(), hash, null))
                .orElseGet(Resolution::invalid);
    }

    public void commit(Resolution resolution) {
        if (resolution.status() != Status.TENTATIVE) return;
        store.save(new ConversationSessionStore.Session(
                resolution.conversationId(), resolution.tokenHash(),
                clock.instant(), clock.instant().plus(ttl)));
    }
    public void clear(Resolution resolution) { store.revoke(resolution.conversationId()); }

    private byte[] hash(ResumeToken token) { return hmac(token.copyBytes()); }
    private byte[] hmac(String value) { return hmac(value.getBytes(StandardCharsets.UTF_8)); }
    private byte[] hmac(byte[] value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(value);
        } catch (Exception failure) { throw new IllegalStateException("session HMAC unavailable", failure); }
    }

    public record Resolution(
            Status status, String conversationId,
            byte[] tokenHash, ResumeToken issuedToken) {
        public Resolution {
            tokenHash = tokenHash == null ? null : tokenHash.clone();
        }
        @Override public byte[] tokenHash() { return tokenHash == null ? null : tokenHash.clone(); }
        static Resolution invalid() { return new Resolution(Status.INVALID, null, null, null); }
    }
    public enum Status { TENTATIVE, AUTHENTICATED, INVALID }
}
