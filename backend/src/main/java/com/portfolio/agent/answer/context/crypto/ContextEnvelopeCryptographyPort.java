package com.portfolio.agent.answer.context.crypto;

import com.portfolio.agent.answer.context.domain.ContextHandle;
import com.portfolio.agent.answer.context.domain.ConversationContextType;
import com.portfolio.agent.answer.context.domain.ConversationId;

import java.util.Objects;

/** Authenticated encryption boundary for a context payload. */
public interface ContextEnvelopeCryptographyPort {
    int MAX_PAYLOAD_BYTES = 16 * 1024;

    SealedContext seal(ConversationId conversationId, ContextHandle contextHandle,
            ConversationContextType contextType, String schemaVersion, byte[] payload);

    byte[] open(ConversationId conversationId, ContextHandle contextHandle,
            ConversationContextType contextType, String schemaVersion, SealedContext sealedContext);

    final class SealedContext {
        private final String keyId;
        private final byte[] nonce;
        private final byte[] ciphertext;

        public SealedContext(String keyId, byte[] nonce, byte[] ciphertext) {
            this.keyId = requireText(keyId, "keyId");
            if (nonce == null || nonce.length != 12) throw new IllegalArgumentException("nonce must be 96 bits");
            if (ciphertext == null || ciphertext.length < 16) throw new IllegalArgumentException("ciphertext is invalid");
            this.nonce = nonce.clone();
            this.ciphertext = ciphertext.clone();
        }
        public String getKeyId() { return keyId; }
        public byte[] getNonce() { return nonce.clone(); }
        public byte[] getCiphertext() { return ciphertext.clone(); }
        public SealedContext withCiphertext(byte[] value) { return new SealedContext(keyId, nonce, value); }
        @Override public String toString() { return "SealedContext{keyId=" + keyId + ", payloadRedacted=true}"; }
        private static String requireText(String value, String name) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
            return value.trim();
        }
    }
}
