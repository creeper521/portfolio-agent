package com.portfolio.agent.turn.state.postgres;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.turn.continuation.ClarificationStore;
import com.portfolio.agent.turn.continuation.ContinuationContext;
import com.portfolio.agent.turn.projection.PublicAgentTurn;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Authenticated bounded codec for the final public snapshot plus state mutations. */
public final class AgentStatePayloadCodec {
    private static final int MAX_BYTES = 128 * 1024;
    private final ObjectMapper mapper;
    private final String keyId;
    private final byte[] key;
    private final SecureRandom random;

    public AgentStatePayloadCodec(ObjectMapper mapper, String keyId, byte[] key) {
        this(mapper, keyId, key, new SecureRandom());
    }
    AgentStatePayloadCodec(ObjectMapper mapper, String keyId, byte[] key, SecureRandom random) {
        this.mapper = mapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        if (keyId == null || keyId.isBlank()) throw new IllegalArgumentException("keyId is required");
        this.keyId = keyId;
        this.key = Objects.requireNonNull(key, "key").clone();
        if (this.key.length != 32) throw new IllegalArgumentException("AES-256 key is required");
        this.random = Objects.requireNonNull(random, "random");
    }

    public Envelope encode(UUID requestId, String conversationId, SettlementPayload payload) {
        return encodeValue(requestId, conversationId, "settlement", payload);
    }

    public Envelope encodeContext(
            UUID requestId, String conversationId, ContinuationContext context) {
        return encodeValue(
                requestId, conversationId, "context:" + context.getContextHandle(), context);
    }
    public ContinuationContext decodeContext(
            UUID requestId, String conversationId, String contextHandle, Envelope envelope) {
        return decodeValue(
                requestId, conversationId, "context:" + contextHandle,
                envelope, ContinuationContext.class);
    }
    public Envelope encodeChallenge(
            UUID requestId, String conversationId, ClarificationStore.Record challenge) {
        return encodeValue(requestId, conversationId,
                "clarification:" + challenge.challenge().getClarificationId(), challenge);
    }
    public ClarificationStore.Record decodeChallenge(
            UUID requestId, String conversationId, String clarificationId, Envelope envelope) {
        return decodeValue(requestId, conversationId,
                "clarification:" + clarificationId, envelope, ClarificationStore.Record.class);
    }

    private Envelope encodeValue(
            UUID requestId, String conversationId, String purpose, Object payload) {
        try {
            byte[] plain = mapper.writeValueAsBytes(payload);
            if (plain.length > MAX_BYTES) throw new IllegalArgumentException("settlement payload is too large");
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce);
            cipher.updateAAD(aad(requestId, conversationId, purpose));
            return new Envelope(keyId, nonce, cipher.doFinal(plain));
        } catch (IllegalArgumentException failure) { throw failure; }
        catch (Exception failure) { throw new IllegalStateException("state payload encryption failed", failure); }
    }

    public SettlementPayload decode(
            UUID requestId, String conversationId, Envelope envelope) {
        return decodeValue(
                requestId, conversationId, "settlement", envelope, SettlementPayload.class);
    }

    private <T> T decodeValue(
            UUID requestId, String conversationId, String purpose,
            Envelope envelope, Class<T> type) {
        if (!keyId.equals(envelope.keyId())) throw new IllegalArgumentException("unknown state key");
        try {
            Cipher cipher = cipher(Cipher.DECRYPT_MODE, envelope.nonce());
            cipher.updateAAD(aad(requestId, conversationId, purpose));
            byte[] plain = cipher.doFinal(envelope.ciphertext());
            if (plain.length > MAX_BYTES) throw new IllegalArgumentException("state payload is too large");
            return mapper.readValue(plain, type);
        } catch (Exception failure) {
            throw new IllegalArgumentException("state payload integrity failed", failure);
        }
    }

    private Cipher cipher(int mode, byte[] nonce) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        return cipher;
    }
    private byte[] aad(UUID requestId, String conversationId, String purpose) {
        return (requestId + "\n" + conversationId + "\n" + purpose + "\nagent-state.v1")
                .getBytes(StandardCharsets.UTF_8);
    }
    public record Envelope(String keyId, byte[] nonce, byte[] ciphertext) {
        public Envelope {
            nonce = nonce.clone(); ciphertext = ciphertext.clone();
            if (nonce.length != 12 || ciphertext.length < 16) {
                throw new IllegalArgumentException("encrypted envelope is invalid");
            }
        }
        @Override public byte[] nonce() { return nonce.clone(); }
        @Override public byte[] ciphertext() { return ciphertext.clone(); }
    }
    public record SettlementPayload(
            PublicAgentTurn publicTurn, List<ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges) {
        public SettlementPayload {
            Objects.requireNonNull(publicTurn, "publicTurn");
            contexts = List.copyOf(contexts); challenges = List.copyOf(challenges);
        }
    }
}
