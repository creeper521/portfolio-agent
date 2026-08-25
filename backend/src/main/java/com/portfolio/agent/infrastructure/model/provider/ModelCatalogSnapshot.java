package com.portfolio.agent.infrastructure.model.provider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 模型目录快照：启动时冻结的、免凭证的可选模型目录。
 *
 * <p>快照同时维护两条视图：内部按 {@link ModelRef} 索引的完整描述符表
 * （仅供服务端解析使用）与对外公开的 {@link ModelCatalogEntry} 列表。
 * 构造期拒绝重复 modelRef，构建后不可变；{@code snapshotVersion} 指纹
 * 由 ConfiguredModelCatalog 从全部公开可见字段派生，用于让客户端
 * 检测目录是否已变化（MODEL_SELECTION_STALE 判定依据）。
 */
public final class ModelCatalogSnapshot {
    private final String snapshotVersion;
    private final Map<ModelRef, ModelProviderDescriptor> descriptors;
    private final List<ModelCatalogEntry> entries;
    private final ModelCatalogDefaultSelection defaultModelSelection;

    /**
     * 构造快照：按 modelRef 建立描述符索引并生成公开条目列表。
     *
     * @param snapshotVersion 非空白指纹，标识这次目录内容
     * @param descriptorList 已通过准入、按展示顺序排列的描述符列表
     * @param defaultModelSelection 安全默认选择
     * @throws IllegalArgumentException 指纹缺失或列表中存在重复 modelRef 时抛出
     */
    public ModelCatalogSnapshot(
            String snapshotVersion,
            List<ModelProviderDescriptor> descriptorList,
            ModelCatalogDefaultSelection defaultModelSelection) {
        if (snapshotVersion == null || snapshotVersion.isBlank()) {
            throw new IllegalArgumentException("snapshotVersion is required");
        }
        this.snapshotVersion = snapshotVersion;
        LinkedHashMap<ModelRef, ModelProviderDescriptor> byRef = new LinkedHashMap<>();
        for (ModelProviderDescriptor descriptor : descriptorList) {
            ModelProviderDescriptor required = Objects.requireNonNull(descriptor, "descriptor");
            if (byRef.putIfAbsent(required.getModelRef(), required) != null) {
                throw new IllegalArgumentException(
                        "duplicate model ref: " + required.getModelRef().value());
            }
        }
        descriptors = Map.copyOf(byRef);
        entries = descriptorList.stream()
                .map(ModelProviderDescriptor::publicEntry)
                .toList();
        this.defaultModelSelection = Objects.requireNonNull(
                defaultModelSelection, "defaultModelSelection");
    }

    /** 空目录快照：无模型、无默认选择，用于 model-runtime 关闭的场景。 */
    public static ModelCatalogSnapshot empty() {
        return new ModelCatalogSnapshot(
                ModelProviderDescriptor.fingerprint("empty-model-catalog"),
                List.of(), ModelCatalogDefaultSelection.none());
    }

    public String getSnapshotVersion() { return snapshotVersion; }
    public List<ModelCatalogEntry> getEntries() { return entries; }
    public ModelCatalogDefaultSelection getDefaultModelSelection() {
        return defaultModelSelection;
    }

    /**
     * 取指定 modelRef 的描述符。
     *
     * @throws IllegalArgumentException 该模型不在冻结目录中（未配置或未通过准入）
     */
    public ModelProviderDescriptor getRequiredDescriptor(ModelRef modelRef) {
        ModelProviderDescriptor descriptor = descriptors.get(
                Objects.requireNonNull(modelRef, "modelRef"));
        if (descriptor == null) {
            throw new IllegalArgumentException(
                    "model is not configured: " + modelRef.value());
        }
        return descriptor;
    }
}
