package com.portfolio.agent.answer.context.codec;

import com.portfolio.agent.answer.context.domain.ConversationContextType;
import com.portfolio.agent.answer.context.domain.RecentSemanticTaskContext;
import com.portfolio.agent.answer.context.domain.RecommendationContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Closed registry for the two P3 context types; no dynamic codec registration. */
public final class ConversationContextCodecRegistry {
    private final Map<String, ConversationContextCodec<?>> codecs;
    private final Map<ConversationContextType, ConversationContextCodec<?>> writers;
    private ConversationContextCodecRegistry(Map<String, ConversationContextCodec<?>> codecs,
                                             Map<ConversationContextType, ConversationContextCodec<?>> writers) {
        this.codecs = Map.copyOf(codecs);
        this.writers = Map.copyOf(writers);
    }
    public static ConversationContextCodecRegistry defaults() {
        Map<ConversationContextType, ConversationContextCodec<?>> legacy = new LinkedHashMap<>();
        legacy.put(ConversationContextType.RECENT_SEMANTIC_TASK, new RecentSemanticTaskContextCodec());
        legacy.put(ConversationContextType.RECOMMENDATION, new RecommendationContextCodec());
        Map<String, ConversationContextCodec<?>> readers = new LinkedHashMap<>();
        for (ConversationContextCodec<?> codec : legacy.values()) readers.put(key(codec.getContextType(), codec.getSchemaVersion()), codec);
        ConversationContextCodec<?> recentV2 = new RecentSemanticTaskContextV2Codec();
        readers.put(key(recentV2.getContextType(), recentV2.getSchemaVersion()), recentV2);
        ConversationContextCodec<?> recommendationV2 = new RecommendationContextV2Codec();
        readers.put(key(recommendationV2.getContextType(), recommendationV2.getSchemaVersion()), recommendationV2);
        Map<ConversationContextType, ConversationContextCodec<?>> writers = new LinkedHashMap<>();
        writers.put(ConversationContextType.RECENT_SEMANTIC_TASK, recentV2);
        writers.put(ConversationContextType.RECOMMENDATION, recommendationV2);
        return new ConversationContextCodecRegistry(readers, writers);
    }
    public EncodedContext encode(ConversationContextType type, Object context) {
        ConversationContextCodec<Object> codec = writer(type);
        if ((type == ConversationContextType.RECENT_SEMANTIC_TASK && !(context instanceof RecentSemanticTaskContext))
                || (type == ConversationContextType.RECOMMENDATION && !(context instanceof RecommendationContext))) throw new IllegalArgumentException("context type does not match payload");
        return new EncodedContext(type, codec.getSchemaVersion(), codec.encode(context));
    }
    public Object decode(EncodedContext encoded) {
        Objects.requireNonNull(encoded, "encoded");
        ConversationContextCodec<Object> codec = reader(encoded.getContextType(), encoded.getSchemaVersion());
        return codec.decode(encoded.getPayload());
    }
    @SuppressWarnings("unchecked")
    private <T> ConversationContextCodec<T> writer(ConversationContextType type) { return (ConversationContextCodec<T>) writers.get(Objects.requireNonNull(type, "type")); }
    @SuppressWarnings("unchecked")
    private <T> ConversationContextCodec<T> reader(ConversationContextType type, String version) {
        ConversationContextCodec<?> codec = codecs.get(key(Objects.requireNonNull(type, "type"), version));
        if (codec == null) throw new IllegalArgumentException("unsupported context schema version");
        return (ConversationContextCodec<T>) codec;
    }
    private static String key(ConversationContextType type, String version) { return type.name() + "@" + version; }

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
