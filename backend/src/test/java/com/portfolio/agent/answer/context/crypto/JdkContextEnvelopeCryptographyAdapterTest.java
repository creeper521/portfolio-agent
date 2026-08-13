package com.portfolio.agent.answer.context.crypto;

import com.portfolio.agent.answer.context.domain.ContextHandle;
import com.portfolio.agent.answer.context.domain.ConversationContextType;
import com.portfolio.agent.answer.context.domain.ConversationId;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdkContextEnvelopeCryptographyAdapterTest {
    @Test
    void encryptsWithGcmAndBindsConversationHandleTypeAndSchemaAsAad() {
        JdkContextEnvelopeCryptographyAdapter adapter = new JdkContextEnvelopeCryptographyAdapter(
                "current", bytes(32, (byte) 7), "previous", bytes(32, (byte) 8));
        ConversationId conversationId = ConversationId.random();
        ContextHandle handle = ContextHandle.issue();
        byte[] payload = "typed-context".getBytes(StandardCharsets.UTF_8);

        ContextEnvelopeCryptographyPort.SealedContext sealed = adapter.seal(
                conversationId, handle, ConversationContextType.RECOMMENDATION,
                "p3-recommendation-v1", payload);

        assertArrayEquals(payload, adapter.open(conversationId, handle,
                ConversationContextType.RECOMMENDATION, "p3-recommendation-v1", sealed));
        assertThrows(ContextIntegrityException.class, () -> adapter.open(
                conversationId, handle, ConversationContextType.RECENT_SEMANTIC_TASK,
                "p3-recommendation-v1", sealed));
        assertThrows(ContextIntegrityException.class, () -> adapter.open(
                conversationId, ContextHandle.issue(), ConversationContextType.RECOMMENDATION,
                "p3-recommendation-v1", sealed));
    }

    @Test
    void readsPreviousKeyAndRejectsOversizedPayloadOrTampering() {
        JdkContextEnvelopeCryptographyAdapter oldAdapter = new JdkContextEnvelopeCryptographyAdapter(
                "old", bytes(32, (byte) 2), null, null);
        JdkContextEnvelopeCryptographyAdapter rotated = new JdkContextEnvelopeCryptographyAdapter(
                "new", bytes(32, (byte) 3), "old", bytes(32, (byte) 2));
        ConversationId conversationId = ConversationId.random();
        ContextHandle handle = ContextHandle.issue();
        ContextEnvelopeCryptographyPort.SealedContext sealed = oldAdapter.seal(
                conversationId, handle, ConversationContextType.RECENT_SEMANTIC_TASK,
                "p3-recent-v1", new byte[] {1, 2, 3});

        assertArrayEquals(new byte[] {1, 2, 3}, rotated.open(conversationId, handle,
                ConversationContextType.RECENT_SEMANTIC_TASK, "p3-recent-v1", sealed));
        byte[] tampered = sealed.getCiphertext();
        tampered[0] ^= 1;
        ContextEnvelopeCryptographyPort.SealedContext changed = sealed.withCiphertext(tampered);
        assertThrows(ContextIntegrityException.class, () -> rotated.open(conversationId, handle,
                ConversationContextType.RECENT_SEMANTIC_TASK, "p3-recent-v1", changed));
        assertThrows(IllegalArgumentException.class, () -> oldAdapter.seal(conversationId, handle,
                ConversationContextType.RECENT_SEMANTIC_TASK, "p3-recent-v1", new byte[16 * 1024 + 1]));
    }

    private static byte[] bytes(int length, byte value) {
        byte[] result = new byte[length];
        java.util.Arrays.fill(result, value);
        return result;
    }
}
