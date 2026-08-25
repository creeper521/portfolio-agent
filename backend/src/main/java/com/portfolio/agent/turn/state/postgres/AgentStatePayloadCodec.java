package com.portfolio.agent.turn.state.postgres;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.portfolio.agent.turn.continuation.ClarificationStore;
import com.portfolio.agent.turn.continuation.ContinuationContext;
import com.portfolio.agent.turn.continuation.ConversationSemanticState;
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

/**
 * Agent State 载荷的认证加密编解码器（AES-256-GCM）。
 *
 * <p>加密终态公众快照、ContinuationContext、challenge 与会话语义状态；AAD 绑定
 * requestId、conversationId、用途与载荷版本，密文被移动到其他行或用途时解密必然
 * 失败。明文上限 {@value MAX_BYTES} 字节；支持用 previous 密钥解密、始终用当前
 * 密钥加密的轮换窗口。Jackson 映射器开启未知字段失败，防止向前兼容地读入合同外
 * 字段。</p>
 */
public final class AgentStatePayloadCodec {
    private static final int MAX_BYTES = 128 * 1024;
    private static final String PAYLOAD_VERSION = "agent-state.v2";
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

    /** 当前密钥集是否包含该 keyId（用于密钥覆盖就绪检查）。 */
    public boolean supportsKey(String candidateKeyId) {
        return candidateKeyId != null && keys.containsKey(candidateKeyId);
    }

    /** 当前可解密的全部密钥 id（当前 + previous）。 */
    public Set<String> supportedKeyIds() {
        return keys.keySet();
    }

    /** 加密一份结算载荷（快照 + 上下文 + challenge）。 */
    public Envelope encode(UUID requestId, String conversationId, SettlementPayload payload) {
        return encodeValue(requestId, conversationId, "settlement", payload);
    }

    /** 加密一个 ContinuationContext，用途绑定 context:{handle}。 */
    public Envelope encodeContext(
            UUID requestId, String conversationId, ContinuationContext context) {
        return encodeValue(
                requestId, conversationId, "context:" + context.getContextHandle(), context);
    }
    /** 解密 ContinuationContext；用途或行不匹配时因 AAD 校验失败而抛错。 */
    public ContinuationContext decodeContext(
            UUID requestId, String conversationId, String contextHandle, Envelope envelope) {
        return decodeValue(
                requestId, conversationId, "context:" + contextHandle,
                envelope, ContinuationContext.class);
    }
    /** 加密会话语义状态（按 conversationId 绑定 AAD）。 */
    public Envelope encodeSemanticState(
            String conversationId, ConversationSemanticState state) {
        return encodeValue(
                UUID.fromString(conversationId), conversationId,
                "conversation-semantic-state", state);
    }
    /** 解密会话语义状态。 */
    public ConversationSemanticState decodeSemanticState(
            String conversationId, Envelope envelope) {
        return decodeValue(
                UUID.fromString(conversationId), conversationId,
                "conversation-semantic-state", envelope,
                ConversationSemanticState.class);
    }
    /** 加密一条 challenge 记录，用途绑定 clarification:{id}。 */
    public Envelope encodeChallenge(
            UUID requestId, String conversationId, ClarificationStore.Record challenge) {
        return encodeValue(requestId, conversationId,
                "clarification:" + challenge.challenge().getClarificationId(), challenge);
    }
    /**
     * 解密 challenge 记录并做双重校验：载荷内 clarificationId 必须与请求一致，
     * 且能通过 ClarificationStore 的形状校验（防篡改/错位密文被采纳）。
     */
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

    /** 加密通用路径：序列化 → 大小上限检查 → 当前密钥 AES-GCM 加密（随机 nonce + AAD）。 */
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

    /** 解密一份结算载荷（settlement 用途）。 */
    public SettlementPayload decode(
            UUID requestId, String conversationId, Envelope envelope) {
        return decodeValue(
                requestId, conversationId, "settlement", envelope, SettlementPayload.class);
    }

    /**
     * 解密通用路径：按 envelope.keyId 选密钥（支持 previous），AAD 校验失败、
     * 大小超限或 JSON 反序列化失败统一折叠为 IllegalArgumentException（完整性失败）。
     */
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
    /** AAD 字节串：requestId + conversationId + 用途 + 载荷版本，绑定密文到唯一位置。 */
    private byte[] aad(UUID requestId, String conversationId, String purpose) {
        return (requestId + "\n" + conversationId + "\n" + purpose + "\n" + PAYLOAD_VERSION)
                .getBytes(StandardCharsets.UTF_8);
    }
    /** 加密信封：密钥 id + 12 字节 nonce + 密文（含 GCM tag，最短 16 字节）；防御性复制。 */
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
    /** 结算载荷：终态公众快照 + 续跑上下文 + challenge 记录（整体加密为一行）。 */
    public record SettlementPayload(
            PublicAgentTurn publicTurn, List<ContinuationContext> contexts,
            List<ClarificationStore.Record> challenges) {
        public SettlementPayload {
            Objects.requireNonNull(publicTurn, "publicTurn");
            contexts = List.copyOf(contexts); challenges = List.copyOf(challenges);
        }
    }
}
