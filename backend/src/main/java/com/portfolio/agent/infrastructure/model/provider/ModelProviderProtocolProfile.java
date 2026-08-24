package com.portfolio.agent.infrastructure.model.provider;

import java.util.Map;

/** Provider-owned request protocol, versioned independently per approved provider. */
public enum ModelProviderProtocolProfile {
    DEEPSEEK_CHAT_COMPLETIONS_V1("deepseek-chat-completions-v1"),
    GLM_CHAT_COMPLETIONS_V1("glm-chat-completions-v1");

    private final String version;

    ModelProviderProtocolProfile(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public void applyStructuredOutputFields(Map<String, Object> payload) {
        payload.put("response_format", Map.of("type", "json_object"));
        payload.put("thinking", Map.of("type", "disabled"));
        payload.put("stream", false);
    }

    public static ModelProviderProtocolProfile forProvider(ModelProviderKind provider) {
        return switch (provider) {
            case DEEPSEEK_V4_FLASH -> DEEPSEEK_CHAT_COMPLETIONS_V1;
            case GLM_4_7 -> GLM_CHAT_COMPLETIONS_V1;
        };
    }
}
