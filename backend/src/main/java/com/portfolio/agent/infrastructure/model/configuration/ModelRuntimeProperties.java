package com.portfolio.agent.infrastructure.model.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/** Closed startup configuration for the selectable model catalog. */
@ConfigurationProperties(prefix = "portfolio.model-runtime")
public final class ModelRuntimeProperties {

    private boolean enabled;
    private String defaultModelRef = "";
    private Map<String, ModelSettings> models = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public String getDefaultModelRef() {
        return defaultModelRef;
    }

    public void setDefaultModelRef(String value) {
        defaultModelRef = value;
    }

    public Map<String, ModelSettings> getModels() {
        return models;
    }

    public void setModels(Map<String, ModelSettings> value) {
        models = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }

    public static final class ModelSettings {
        private boolean enabled;
        private boolean selectable = true;
        private String displayName = "";
        private int displayOrder;
        private String selectionVersion = "";
        private String endpoint = "";
        private String model = "";
        private String apiKey = "";
        private String protocolProfile = "";
        private boolean dataPolicyApproved;
        private String structuredOutput = "JSON_OBJECT";
        private String thinkingMode = "DISABLED";
        private boolean streaming;
        private int maxContextTokens;
        private int maxOutputTokens;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { enabled = value; }
        public boolean isSelectable() { return selectable; }
        public void setSelectable(boolean value) { selectable = value; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String value) { displayName = value; }
        public int getDisplayOrder() { return displayOrder; }
        public void setDisplayOrder(int value) { displayOrder = value; }
        public String getSelectionVersion() { return selectionVersion; }
        public void setSelectionVersion(String value) { selectionVersion = value; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String value) { endpoint = value; }
        public String getModel() { return model; }
        public void setModel(String value) { model = value; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String value) { apiKey = value; }
        public String getProtocolProfile() { return protocolProfile; }
        public void setProtocolProfile(String value) { protocolProfile = value; }
        public boolean isDataPolicyApproved() { return dataPolicyApproved; }
        public void setDataPolicyApproved(boolean value) { dataPolicyApproved = value; }
        public String getStructuredOutput() { return structuredOutput; }
        public void setStructuredOutput(String value) { structuredOutput = value; }
        public String getThinkingMode() { return thinkingMode; }
        public void setThinkingMode(String value) { thinkingMode = value; }
        public boolean isStreaming() { return streaming; }
        public void setStreaming(boolean value) { streaming = value; }
        public int getMaxContextTokens() { return maxContextTokens; }
        public void setMaxContextTokens(int value) { maxContextTokens = value; }
        public int getMaxOutputTokens() { return maxOutputTokens; }
        public void setMaxOutputTokens(int value) { maxOutputTokens = value; }
    }
}
