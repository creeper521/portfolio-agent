package com.portfolio.agent.answer.context.crypto;

import com.portfolio.agent.answer.context.domain.ResumeToken;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** JDK HMAC-SHA-256 implementation with current-write/previous-read rotation. */
public final class JdkResumeTokenHashAdapter implements ResumeTokenHashPort {
    private final String currentKeyId;
    private final Map<String, byte[]> readKeys;

    public JdkResumeTokenHashAdapter(
            String currentKeyId, byte[] currentKey, String previousKeyId, byte[] previousKey) {
        this.currentKeyId = keyId(currentKeyId);
        this.readKeys = new LinkedHashMap<>();
        this.readKeys.put(this.currentKeyId, key(currentKey));
        if (previousKeyId != null || previousKey != null) {
            if (previousKeyId == null || previousKey == null) throw new IllegalArgumentException("previous token key is incomplete");
            this.readKeys.put(keyId(previousKeyId), key(previousKey));
        }
    }

    @Override
    public HashedToken hash(ResumeToken token) {
        Objects.requireNonNull(token, "token");
        return new HashedToken(currentKeyId, digest(readKeys.get(currentKeyId), token));
    }

    @Override
    public boolean matches(ResumeToken token, HashedToken hash) {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(hash, "hash");
        byte[] key = readKeys.get(hash.getKeyId());
        if (key == null) return false;
        return MessageDigest.isEqual(hash.getDigest(), digest(key, token));
    }

    private static byte[] digest(byte[] key, ResumeToken token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(token.copyBytes());
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("resume token hashing is unavailable", exception);
        }
    }
    private static String keyId(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("keyId is required");
        return value.trim();
    }
    private static byte[] key(byte[] value) {
        if (value == null || value.length < 32) throw new IllegalArgumentException("HMAC key must be at least 32 bytes");
        return value.clone();
    }
}
