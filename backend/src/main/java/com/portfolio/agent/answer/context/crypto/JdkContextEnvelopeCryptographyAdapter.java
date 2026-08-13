package com.portfolio.agent.answer.context.crypto;

import com.portfolio.agent.answer.context.domain.ContextHandle;
import com.portfolio.agent.answer.context.domain.ConversationContextType;
import com.portfolio.agent.answer.context.domain.ConversationId;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** AES-256-GCM context envelope with 96-bit nonce and AAD binding. */
public final class JdkContextEnvelopeCryptographyAdapter implements ContextEnvelopeCryptographyPort {
    private final String currentKeyId;
    private final Map<String, byte[]> readKeys;
    private final SecureRandom secureRandom;

    public JdkContextEnvelopeCryptographyAdapter(
            String currentKeyId, byte[] currentKey, String previousKeyId, byte[] previousKey) {
        this(currentKeyId, currentKey, previousKeyId, previousKey, new SecureRandom());
    }

    public JdkContextEnvelopeCryptographyAdapter(
            String currentKeyId, byte[] currentKey, String previousKeyId, byte[] previousKey,
            SecureRandom secureRandom) {
        this.currentKeyId = requireText(currentKeyId, "currentKeyId");
        this.readKeys = new LinkedHashMap<>();
        this.readKeys.put(this.currentKeyId, key(currentKey));
        if (previousKeyId != null || previousKey != null) {
            if (previousKeyId == null || previousKey == null) throw new IllegalArgumentException("previous payload key is incomplete");
            this.readKeys.put(requireText(previousKeyId, "previousKeyId"), key(previousKey));
        }
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    @Override
    public SealedContext seal(ConversationId conversationId, ContextHandle contextHandle,
            ConversationContextType contextType, String schemaVersion, byte[] payload) {
        validate(conversationId, contextHandle, contextType, schemaVersion, payload);
        byte[] nonce = new byte[12];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(readKeys.get(currentKeyId), "AES"),
                    new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad(conversationId, contextHandle, contextType, schemaVersion));
            return new SealedContext(currentKeyId, nonce, cipher.doFinal(payload));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("context encryption is unavailable", exception);
        }
    }

    @Override
    public byte[] open(ConversationId conversationId, ContextHandle contextHandle,
            ConversationContextType contextType, String schemaVersion, SealedContext sealedContext) {
        Objects.requireNonNull(sealedContext, "sealedContext");
        byte[] selectedKey = readKeys.get(sealedContext.getKeyId());
        if (selectedKey == null) throw new ContextIntegrityException();
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(selectedKey, "AES"),
                    new GCMParameterSpec(128, sealedContext.getNonce()));
            cipher.updateAAD(aad(conversationId, contextHandle, contextType, schemaVersion));
            byte[] plain = cipher.doFinal(sealedContext.getCiphertext());
            if (plain.length > MAX_PAYLOAD_BYTES) throw new ContextIntegrityException();
            return plain;
        } catch (ContextIntegrityException exception) {
            throw exception;
        } catch (GeneralSecurityException | NullPointerException | IllegalArgumentException exception) {
            throw new ContextIntegrityException();
        }
    }

    private static void validate(ConversationId conversationId, ContextHandle contextHandle,
            ConversationContextType contextType, String schemaVersion, byte[] payload) {
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(contextHandle, "contextHandle");
        Objects.requireNonNull(contextType, "contextType");
        requireText(schemaVersion, "schemaVersion");
        if (payload == null || payload.length > MAX_PAYLOAD_BYTES) throw new IllegalArgumentException("context payload exceeds 16KiB");
    }
    private static byte[] aad(ConversationId conversationId, ContextHandle contextHandle,
            ConversationContextType contextType, String schemaVersion) {
        return (conversationId + "\n" + contextHandle.asBase64Url() + "\n" + contextType.name() + "\n" + requireText(schemaVersion, "schemaVersion"))
                .getBytes(StandardCharsets.UTF_8);
    }
    private static byte[] key(byte[] value) {
        if (value == null || value.length != 32) throw new IllegalArgumentException("AES key must be 256 bits");
        return value.clone();
    }
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
