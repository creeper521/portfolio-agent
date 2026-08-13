package com.portfolio.agent.answer.context.crypto;

import com.portfolio.agent.answer.context.domain.ResumeToken;

/** HMAC-backed opaque token hashing boundary. */
public interface ResumeTokenHashPort {
    HashedToken hash(ResumeToken token);
    boolean matches(ResumeToken token, HashedToken hash);

    final class HashedToken {
        private final String keyId;
        private final byte[] digest;
        public HashedToken(String keyId, byte[] digest) {
            if (keyId == null || keyId.isBlank()) throw new IllegalArgumentException("keyId is required");
            if (digest == null || digest.length != 32) throw new IllegalArgumentException("digest must be 32 bytes");
            this.keyId = keyId.trim();
            this.digest = digest.clone();
        }
        public String getKeyId() { return keyId; }
        public byte[] getDigest() { return digest.clone(); }
        @Override public String toString() { return "HashedToken{keyId=" + keyId + ", digestRedacted=true}"; }
    }
}
