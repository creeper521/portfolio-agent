package com.portfolio.agent.infrastructure.model.provider;

import java.util.Set;

/**
 * 公开目录条目：免秘密的模型投影（modelRef、显示名、排序、选择版本、能力集）。
 *
 * <p>由 {@link ModelProviderDescriptor#publicEntry()} 生成并进入目录快照的
 * 公开 entries，不携带 endpoint、模型名等任何服务端字段。紧凑构造器
 * 校验文本非空、能力集非空并做防御性拷贝。
 */
public record ModelCatalogEntry(
        String modelRef,
        String displayName,
        int displayOrder,
        String selectionVersion,
        Set<ModelCapability> capabilities) {

    public ModelCatalogEntry {
        modelRef = requireText(modelRef, "modelRef");
        displayName = requireText(displayName, "displayName");
        selectionVersion = requireText(selectionVersion, "selectionVersion");
        capabilities = Set.copyOf(capabilities);
        if (capabilities.isEmpty()) {
            throw new IllegalArgumentException("capabilities must not be empty");
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
