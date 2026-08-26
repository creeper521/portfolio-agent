package com.portfolio.agent.infrastructure.model;

import com.portfolio.agent.infrastructure.model.provider.ModelProviderProtocolProfile;
import com.portfolio.agent.infrastructure.model.provider.ModelRef;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.structured.OperationBinding;

import java.net.URI;
import java.util.Objects;
import java.util.Map;

/**
 * 模型传输绑定：某个 modelRef 对应的服务端专用 Provider 调用参数。
 *
 * <p>与 {@link ModelExecutionSnapshot} 相对：快照是免凭证、可公开投影的模型
 * 权威，而绑定持有 endpoint 与 API key，只允许服务端传输层使用——凭证的读取
 * 方法（{@code authorizationHeaderValue}）被刻意收窄为包私有，避免泄漏到
 * 更上层。
 *
 * <p>构造期即完成 fail-closed 校验：endpoint 必须是仅含 host 的 HTTPS URI
 * （拒绝 userInfo、query、fragment 等可能夹带凭证的成分），凭证与模型名必须
 * 为非空文本，输出 token 上限必须落在 1..128000 区间。任何一项不满足都直接
 * 抛出 IllegalArgumentException，阻止非法绑定进入运行期。
 */
public final class ModelTransportBinding {
    private final ModelRef modelRef;
    private final String descriptorFingerprint;
    private final URI endpoint;
    private final String modelName;
    private final ModelProviderProtocolProfile protocolProfile;
    private final String apiKey;
    private final int maxOutputTokens;
    private final Map<ModelOperation, OperationBinding> operationBindings;

    public ModelTransportBinding(
            ModelRef modelRef,
            String descriptorFingerprint,
            URI endpoint,
            String modelName,
            ModelProviderProtocolProfile protocolProfile,
            String apiKey,
            int maxOutputTokens,
            Map<ModelOperation, OperationBinding> operationBindings) {
        this.modelRef = Objects.requireNonNull(modelRef, "modelRef");
        this.descriptorFingerprint = requireFingerprint(descriptorFingerprint);
        this.endpoint = requireHttpsEndpoint(endpoint);
        this.modelName = requireText(modelName, "modelName");
        this.protocolProfile = Objects.requireNonNull(protocolProfile, "protocolProfile");
        this.apiKey = requireText(apiKey, "credential");
        if (maxOutputTokens < 1 || maxOutputTokens > 128_000) {
            throw new IllegalArgumentException("maxOutputTokens is invalid");
        }
        this.maxOutputTokens = maxOutputTokens;
        this.operationBindings = copyBindings(operationBindings);
    }

    public ModelRef getModelRef() { return modelRef; }
    public String getDescriptorFingerprint() { return descriptorFingerprint; }
    public URI getEndpoint() { return endpoint; }
    public String getModelName() { return modelName; }
    public ModelProviderProtocolProfile getProtocolProfile() { return protocolProfile; }
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public Map<ModelOperation, OperationBinding> getOperationBindings() {
        return operationBindings;
    }

    public OperationBinding getRequiredOperationBinding(ModelOperation operation) {
        OperationBinding binding = operationBindings.get(
                Objects.requireNonNull(operation, "operation"));
        if (binding == null) {
            throw new IllegalArgumentException("model operation binding is not available");
        }
        return binding;
    }

    /** 包私有的凭证读取：仅传输层可组装 Authorization 头，防止凭证外流。 */
    String authorizationHeaderValue() {
        return "Bearer " + apiKey;
    }

    /** 校验非空、非空白文本并去除首尾空白。 */
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.strip();
    }

    /**
     * 校验 endpoint 是"干净"的 HTTPS URI：必须为 https 协议且带 host，
     * 且不得携带 userInfo、query 或 fragment。
     * 这些成分可能夹带凭证或旁路参数，一律在构造期拒绝。
     */
    private static URI requireHttpsEndpoint(URI value) {
        if (value == null
                || !"https".equalsIgnoreCase(value.getScheme())
                || value.getHost() == null
                || value.getHost().isBlank()
                || value.getUserInfo() != null
                || value.getRawQuery() != null
                || value.getFragment() != null) {
            throw new IllegalArgumentException("endpoint must be an HTTPS URI with a host");
        }
        return value;
    }

    private static String requireFingerprint(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("descriptorFingerprint is invalid");
        }
        return value;
    }

    private static Map<ModelOperation, OperationBinding> copyBindings(
            Map<ModelOperation, OperationBinding> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("operationBindings must not be empty");
        }
        for (Map.Entry<ModelOperation, OperationBinding> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null
                    || entry.getKey() != entry.getValue().getOperation()) {
                throw new IllegalArgumentException("operationBindings are invalid");
            }
        }
        return Map.copyOf(values);
    }
}
