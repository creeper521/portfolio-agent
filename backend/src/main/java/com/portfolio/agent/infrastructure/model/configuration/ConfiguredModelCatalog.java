package com.portfolio.agent.infrastructure.model.configuration;

import com.portfolio.agent.infrastructure.model.ModelTransportBinding;
import com.portfolio.agent.infrastructure.model.provider.ModelCapability;
import com.portfolio.agent.infrastructure.model.provider.ModelCatalogDefaultSelection;
import com.portfolio.agent.infrastructure.model.provider.ModelCatalogEntry;
import com.portfolio.agent.infrastructure.model.provider.ModelCatalogSnapshot;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderDescriptor;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderProtocolProfile;
import com.portfolio.agent.infrastructure.model.provider.ModelRef;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 配置驱动的模型目录：从启动配置构建冻结的目录快照与服务端专用传输绑定。
 *
 * <p>这是三重准入中第一、二重的落地点：
 * <ul>
 *   <li>model-runtime 总开关未启用时，产出空目录、空描述符表与空绑定表——
 *       一切模型能力保持关闭；</li>
 *   <li>每个模型必须通过 enabled、data-policy 批准与凭证存在性三道门槛，
 *       才会生成内部描述符与传输绑定；只有再满足 selectable 的模型才会
 *       进入公开目录快照；</li>
 *   <li>协议参数必须是封闭取值（结构化输出 JSON_OBJECT、thinking 禁用、
 *       非 streaming），endpoint 必须是合法 URI，否则启动直接失败。</li>
 * </ul>
 *
 * <p>构造完成后目录即冻结：快照版本指纹由默认选择与全部公开条目的
 * 关键字段派生，后续配置变化不会影响已构建实例。
 */
public final class ConfiguredModelCatalog {
    private static final Set<ModelCapability> CAPABILITIES = Set.of(
            ModelCapability.TURN_INTERPRETATION,
            ModelCapability.GENERAL_KNOWLEDGE);

    private final ModelCatalogSnapshot snapshot;
    private final Map<ModelRef, ModelProviderDescriptor> internalDescriptors;
    private final Map<ModelRef, ModelTransportBinding> bindings;

    /**
     * 从启动配置构建目录。总开关关闭时产出全空目录；开启时逐模型执行
     * 准入：enabled 与否决定是否考虑、data-policy 与凭证决定是否有内部
     * 描述符与绑定、selectable 决定是否进入公开快照。公开条目按
     * displayOrder 再按 modelRef 排序；配置的默认模型不在可选列表中时
     * 默认选择退化为 none（不回退到其他模型）。
     *
     * @param properties model-runtime 启动配置
     * @throws IllegalArgumentException 默认 modelRef 未配置、模型设置非法
     *         （协议参数不封闭、endpoint 非法、必填文本缺失等）时启动失败
     */
    public ConfiguredModelCatalog(ModelRuntimeProperties properties) {
        Objects.requireNonNull(properties, "properties");
        if (!properties.isEnabled()) {
            snapshot = ModelCatalogSnapshot.empty();
            internalDescriptors = Map.of();
            bindings = Map.of();
            return;
        }

        Map<String, ModelRuntimeProperties.ModelSettings> configured =
                properties.getModels();
        ModelRef defaultRef = configuredDefault(properties.getDefaultModelRef(), configured);
        List<ConfiguredEntry> selectable = new ArrayList<>();
        LinkedHashMap<ModelRef, ModelProviderDescriptor> internalByRef =
                new LinkedHashMap<>();
        LinkedHashMap<ModelRef, ModelTransportBinding> bindingByRef =
                new LinkedHashMap<>();
        for (Map.Entry<String, ModelRuntimeProperties.ModelSettings> entry
                : configured.entrySet()) {
            ModelRef ref = ModelRef.of(entry.getKey());
            ModelRuntimeProperties.ModelSettings settings =
                    Objects.requireNonNull(entry.getValue(), "model settings");
            if (!settings.isEnabled()) {
                continue;
            }
            ModelProviderDescriptor descriptor = descriptor(ref, settings);
            if (!settings.isDataPolicyApproved()
                    || isBlank(settings.getApiKey())) {
                continue;
            }
            ModelTransportBinding binding = new ModelTransportBinding(
                    ref, descriptor.getEndpoint(), descriptor.getModelName(),
                    descriptor.getProtocolProfile(), settings.getApiKey(),
                    descriptor.getMaxOutputTokens());
            internalByRef.put(ref, descriptor);
            bindingByRef.put(ref, binding);
            if (settings.isSelectable()) {
                selectable.add(new ConfiguredEntry(descriptor, binding));
            }
        }
        selectable.sort(Comparator
                .comparingInt((ConfiguredEntry entry) ->
                        entry.descriptor().getDisplayOrder())
                .thenComparing(entry -> entry.descriptor().getModelRef()));

        List<ModelProviderDescriptor> descriptors = selectable.stream()
                .map(ConfiguredEntry::descriptor)
                .toList();
        internalDescriptors = Map.copyOf(internalByRef);
        bindings = Map.copyOf(bindingByRef);

        ModelProviderDescriptor defaultDescriptor = descriptors.stream()
                .filter(descriptor -> descriptor.getModelRef().equals(defaultRef))
                .findFirst().orElse(null);
        ModelCatalogDefaultSelection defaultSelection = defaultDescriptor == null
                ? ModelCatalogDefaultSelection.none()
                : ModelCatalogDefaultSelection.model(defaultDescriptor);
        snapshot = new ModelCatalogSnapshot(
                snapshotVersion(descriptors, defaultSelection),
                descriptors, defaultSelection);
    }

    /** 返回冻结的目录快照（总开关关闭时为空快照）。 */
    public ModelCatalogSnapshot snapshot() {
        return snapshot;
    }

    /**
     * 取指定 modelRef 的服务端传输绑定。
     *
     * @throws IllegalArgumentException 该模型未配置或未通过准入
     *         （enabled/data-policy/凭证任一不满足）时抛出
     */
    public ModelTransportBinding getRequiredBinding(ModelRef modelRef) {
        ModelTransportBinding binding = bindings.get(
                Objects.requireNonNull(modelRef, "modelRef"));
        if (binding == null) {
            throw new IllegalArgumentException(
                    "model transport is not configured: " + modelRef.value());
        }
        return binding;
    }

    /**
     * 取指定 modelRef 的内部描述符（含 endpoint 等服务端字段），
     * 仅限基础设施内部装配使用。
     *
     * @throws IllegalArgumentException 该模型未进入内部描述符表时抛出
     */
    ModelProviderDescriptor getRequiredInternalDescriptor(ModelRef modelRef) {
        ModelProviderDescriptor descriptor = internalDescriptors.get(
                Objects.requireNonNull(modelRef, "modelRef"));
        if (descriptor == null) {
            throw new IllegalArgumentException(
                    "model descriptor is not available for internal execution: "
                            + modelRef.value());
        }
        return descriptor;
    }

    /** 校验配置的默认 modelRef：必须非空白且真实存在于模型配置中，否则启动失败。 */
    private ModelRef configuredDefault(
            String configuredDefault,
            Map<String, ModelRuntimeProperties.ModelSettings> configured) {
        if (isBlank(configuredDefault)) {
            return null;
        }
        ModelRef defaultRef = ModelRef.of(configuredDefault);
        if (!configured.containsKey(defaultRef.value())) {
            throw new IllegalArgumentException("default model ref is not configured");
        }
        return defaultRef;
    }

    /**
     * 把单个模型设置转换为描述符，并强制封闭协议取值：
     * structured output 必须为 JSON_OBJECT、thinking 必须禁用、streaming
     * 必须关闭、endpoint 必须是可解析的非空 URI。任一不满足即抛出
     * IllegalArgumentException，阻止未批准的协议形态进入运行期。
     */
    private ModelProviderDescriptor descriptor(
            ModelRef ref, ModelRuntimeProperties.ModelSettings settings) {
        requireExact(settings.getStructuredOutput(), "JSON_OBJECT", "structured output");
        requireExact(settings.getThinkingMode(), "DISABLED", "thinking mode");
        if (settings.isStreaming()) {
            throw new IllegalArgumentException("streaming must be disabled");
        }
        ModelProviderProtocolProfile profile =
                ModelProviderProtocolProfile.fromConfiguredName(
                        settings.getProtocolProfile());
        URI endpoint;
        try {
            endpoint = URI.create(requireText(settings.getEndpoint(), "endpoint"));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException(
                    "endpoint must be an HTTPS URI with a host", failure);
        }
        return new ModelProviderDescriptor(
                ref,
                requireText(settings.getSelectionVersion(), "selectionVersion"),
                requireText(settings.getDisplayName(), "displayName"),
                settings.getDisplayOrder(),
                endpoint,
                requireText(settings.getModel(), "model"),
                profile,
                CAPABILITIES,
                settings.getMaxContextTokens(),
                settings.getMaxOutputTokens());
    }

    /**
     * 由默认选择形态与全部公开条目的关键字段（modelRef、显示名、排序、
     * 版本、能力指纹）派生目录快照版本指纹，任何公开可见变化都会改变指纹。
     */
    private String snapshotVersion(
            List<ModelProviderDescriptor> descriptors,
            ModelCatalogDefaultSelection defaultSelection) {
        List<String> values = new ArrayList<>();
        values.add(defaultSelection.kind().name());
        values.add(defaultSelection.modelRef() == null ? "" : defaultSelection.modelRef());
        values.add(defaultSelection.selectionVersion() == null
                ? "" : defaultSelection.selectionVersion());
        for (ModelProviderDescriptor descriptor : descriptors) {
            ModelCatalogEntry entry = descriptor.publicEntry();
            values.add(entry.modelRef());
            values.add(entry.displayName());
            values.add(Integer.toString(entry.displayOrder()));
            values.add(entry.selectionVersion());
            values.add(ModelProviderDescriptor.canonicalCapabilities(
                    entry.capabilities()));
        }
        return ModelProviderDescriptor.fingerprint(values.toArray(String[]::new));
    }

    private String requireText(String value, String name) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.strip();
    }

    private void requireExact(String value, String expected, String name) {
        if (!expected.equals(value)) {
            throw new IllegalArgumentException(name + " must be " + expected);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 内部装配用条目对：描述符与其绑定，仅在排序时携带两者。 */
    private record ConfiguredEntry(
            ModelProviderDescriptor descriptor,
            ModelTransportBinding binding) { }
}
