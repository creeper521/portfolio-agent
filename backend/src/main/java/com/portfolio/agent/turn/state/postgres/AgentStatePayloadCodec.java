package com.portfolio.agent.turn.state.postgres;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.portfolio.agent.turn.continuation.ClarificationStore;
import com.portfolio.agent.turn.continuation.ContinuationContext;
import com.portfolio.agent.turn.projection.PublicAgentTurn;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Authenticated bounded codec for the final public snapshot plus state mutations. */
public final class AgentStatePayloadCodec {
    private static final int MAX_BYTES = 128 * 1024;
    private final ObjectMapper mapper;
    private final String currentKeyId;
    private final Map<String, byte[]> keys;
    private final SecureRandom random;

    public AgentStatePayloadCodec(ObjectMapper mapper, String keyId, byte[] key) {
        this(mapper, keyId, key, Map.of());
    }
    public AgentStatePayloadCodec(
            ObjectMapper mapper, String keyId, byte[] key,
            Map<String, byte[]> previousKeys) {
        this(mapper, keyId, key, previousKeys, new SecureRandom());
    }
    AgentStatePayloadCodec(
            ObjectMapper mapper, String keyId, byte[] key,
            Map<String, byte[]> previousKeys, SecureRandom random) {
        this.mapper = mapper.copy()
                .setSerializationInclusion(JsonInclude.Include.ALWAYS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        if (keyId == null || keyId.isBlank()) throw new IllegalArgumentException("keyId is required");
        this.currentKeyId = keyId;
        LinkedHashMap<String, byte[]> configuredKeys = new LinkedHashMap<>();
        configuredKeys.put(keyId, requireKey(key));
        Objects.requireNonNull(previousKeys, "previousKeys").forEach((previousId, previousKey) -> {
            if (previousId == null || previousId.isBlank() || previousId.equals(keyId)) {
                throw new IllegalArgumentException("previous key id is invalid");
            }
            configuredKeys.put(previousId, requireKey(previousKey));
        });
        this.keys = Map.copyOf(configuredKeys);
        this.random = Objects.requireNonNull(random, "random");
    }

    public boolean supportsKey(String candidateKeyId) {
        return candidateKeyId != null && keys.containsKey(candidateKeyId);
    }

    public Set<String> supportedKeyIds() {
        return keys.keySet();
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
        ClarificationStore.Record decoded = decodeValue(requestId, conversationId,
                "clarification:" + clarificationId, envelope, ClarificationStore.Record.class);
        if (!decoded.challenge().getClarificationId().equals(clarificationId)) {
            throw new IllegalArgumentException("clarification payload id mismatch");
        }
        ClarificationStore validator = new ClarificationStore(
                java.time.Clock.systemUTC(), java.time.Duration.ofMinutes(1));
        validator.save(decoded);
        return decoded;
    }

    private Envelope encodeValue(
            UUID requestId, String conversationId, String purpose, Object payload) {
        try {
            byte[] plain = mapper.writeValueAsBytes(payload);
            if (plain.length > MAX_BYTES) throw new IllegalArgumentException("settlement payload is too large");
            byte[] nonce = new byte[12]; random.nextBytes(nonce);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce, keys.get(currentKeyId));
            cipher.updateAAD(aad(requestId, conversationId, purpose));
            return new Envelope(currentKeyId, nonce, cipher.doFinal(plain));
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
        byte[] decodeKey = keys.get(envelope.keyId());
        if (decodeKey == null) throw new IllegalArgumentException("unknown state key");
        try {
            Cipher cipher = cipher(Cipher.DECRYPT_MODE, envelope.nonce(), decodeKey);
            cipher.updateAAD(aad(requestId, conversationId, purpose));
            byte[] plain = cipher.doFinal(envelope.ciphertext());
            if (plain.length > MAX_BYTES) throw new IllegalArgumentException("state payload is too large");
            return mapper.readValue(plain, type);
        } catch (Exception failure) {
            throw new IllegalArgumentException("state payload integrity failed", failure);
        }
    }

    private byte[] requireKey(byte[] value) {
        byte[] copy = Objects.requireNonNull(value, "key").clone();
        if (copy.length != 32) throw new IllegalArgumentException("AES-256 key is required");
        return copy;
    }
    private Cipher cipher(int mode, byte[] nonce, byte[] key) throws Exception {
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
