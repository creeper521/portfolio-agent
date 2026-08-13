package com.portfolio.agent.answer.context.codec;

import com.portfolio.agent.answer.context.domain.ConversationContextType;
import com.portfolio.agent.answer.context.domain.RecentSemanticTaskContext;
import com.portfolio.agent.answer.context.domain.RecommendationContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Closed registry for the two P3 context types; no dynamic codec registration. */
public final class ConversationContextCodecRegistry {
    private final Map<ConversationContextType, ConversationContextCodec<?>> codecs;
    private ConversationContextCodecRegistry(Map<ConversationContextType, ConversationContextCodec<?>> codecs) {
        this.codecs = Map.copyOf(codecs);
    }
    public static ConversationContextCodecRegistry defaults() {
        Map<ConversationContextType, ConversationContextCodec<?>> codecs = new LinkedHashMap<>();
        codecs.put(ConversationContextType.RECENT_SEMANTIC_TASK, new RecentSemanticTaskContextCodec());
        codecs.put(ConversationContextType.RECOMMENDATION, new RecommendationContextCodec());
        return new ConversationContextCodecRegistry(codecs);
    }
    public EncodedContext encode(ConversationContextType type, Object context) {
        ConversationContextCodec<Object> codec = codec(type);
        if ((type == ConversationContextType.RECENT_SEMANTIC_TASK && !(context instanceof RecentSemanticTaskContext))
                || (type == ConversationContextType.RECOMMENDATION && !(context instanceof RecommendationContext))) throw new IllegalArgumentException("context type does not match payload");
        return new EncodedContext(type, codec.getSchemaVersion(), codec.encode(context));
    }
    public Object decode(EncodedContext encoded) {
        Objects.requireNonNull(encoded, "encoded");
        ConversationContextCodec<Object> codec = codec(encoded.getContextType());
        if (!codec.getSchemaVersion().equals(encoded.getSchemaVersion())) throw new IllegalArgumentException("unsupported context schema version");
        return codec.decode(encoded.getPayload());
    }
    @SuppressWarnings("unchecked")
    private <T> ConversationContextCodec<T> codec(ConversationContextType type) { return (ConversationContextCodec<T>) codecs.get(Objects.requireNonNull(type, "type")); }

    public static final class EncodedContext {
        private final ConversationContextType contextType;
        private final String schemaVersion;
        private final byte[] payload;
        public EncodedContext(ConversationContextType contextType, String schemaVersion, byte[] payload) {
            this.contextType = Objects.requireNonNull(contextType, "contextType");
            if (schemaVersion == null || schemaVersion.isBlank()) throw new IllegalArgumentException("schemaVersion is required");
            if (payload == null || payload.length > 16 * 1024) throw new IllegalArgumentException("context payload exceeds 16KiB");
            this.schemaVersion = schemaVersion.trim();
            this.payload = payload.clone();
        }
        public ConversationContextType getContextType() { return contextType; }
        public String getSchemaVersion() { return schemaVersion; }
        public byte[] getPayload() { return payload.clone(); }
    }
}
