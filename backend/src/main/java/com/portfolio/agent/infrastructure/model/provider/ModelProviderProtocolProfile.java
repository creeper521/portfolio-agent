package com.portfolio.agent.infrastructure.model.provider;

import java.util.Map;

/** Closed request shapes admitted by the structured transport. */
public enum ModelProviderProtocolProfile {
    ZHIPU_CHAT_COMPLETIONS("zhipu-chat-completions-v1") {
        @Override
        public void applyStructuredOutputFields(Map<String, Object> payload) {
            common(payload);
            payload.put("thinking", Map.of("type", "disabled"));
        }
    },
    DASHSCOPE_CHAT_COMPLETIONS("dashscope-chat-completions-v1") {
        @Override
        public void applyStructuredOutputFields(Map<String, Object> payload) {
            common(payload);
            payload.put("enable_thinking", false);
        }
    };

    private final String version;

    ModelProviderProtocolProfile(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public abstract void applyStructuredOutputFields(Map<String, Object> payload);

    public static ModelProviderProtocolProfile fromConfiguredName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("protocol profile is required");
        }
        return switch (value) {
            case "ZHIPU_CHAT_COMPLETIONS" -> ZHIPU_CHAT_COMPLETIONS;
            case "DASHSCOPE_CHAT_COMPLETIONS" -> DASHSCOPE_CHAT_COMPLETIONS;
            default -> throw new IllegalArgumentException(
                    "protocol profile is not approved: " + safeCategory(value));
        };
    }

    private static void common(Map<String, Object> payload) {
        payload.put("response_format", Map.of("type", "json_object"));
        payload.put("stream", false);
    }

    private static String safeCategory(String value) {
        return value.isBlank() ? "BLANK" : "UNKNOWN";
    }
}
