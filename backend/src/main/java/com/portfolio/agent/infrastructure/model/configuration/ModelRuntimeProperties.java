package com.portfolio.agent.infrastructure.model.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * model-runtime 启动配置：可选模型目录的封闭总开关与逐模型设置。
 *
 * <p>这是三重准入第一重的配置入口：{@code enabled} 未显式开启时，
 * {@link ConfiguredModelCatalog} 会产出空目录，一切模型能力保持关闭。
 * 每个模型的 enabled/selectable/credential(data-policy) 字段构成第二重
 * Provider 准入；协议组合只能通过代码批准的 executionProfile 选择。
 */
@ConfigurationProperties(prefix = "portfolio.model-runtime")
public final class ModelRuntimeProperties {

    /** model-runtime 总开关，缺省关闭（fail-closed）。 */
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

    /**
     * 单个模型的启动设置：准入四要素（enabled/selectable/apiKey/
     * dataPolicyApproved）加展示、版本、执行画像与 token 上限。
     */
    public static final class ModelSettings {
        private boolean enabled;
        private boolean selectable = true;
        private String displayName = "";
        private int displayOrder;
        private String selectionVersion = "";
        private String endpoint = "";
        private String model = "";
        private String apiKey = "";
        private String executionProfile = "";
        private boolean dataPolicyApproved;
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
        public String getExecutionProfile() { return executionProfile; }
        public void setExecutionProfile(String value) { executionProfile = value; }
        public boolean isDataPolicyApproved() { return dataPolicyApproved; }
        public void setDataPolicyApproved(boolean value) { dataPolicyApproved = value; }
        public int getMaxContextTokens() { return maxContextTokens; }
        public void setMaxContextTokens(int value) { maxContextTokens = value; }
        public int getMaxOutputTokens() { return maxOutputTokens; }
        public void setMaxOutputTokens(int value) { maxOutputTokens = value; }
    }
}
