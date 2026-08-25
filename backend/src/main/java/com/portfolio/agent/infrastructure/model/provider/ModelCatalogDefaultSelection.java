package com.portfolio.agent.infrastructure.model.provider;

/**
 * 随目录一起投影的安全默认选择：仅含 modelRef 与 selectionVersion，
 * 不含任何服务端字段。
 *
 * <p>紧凑构造器保证形态一致：MODEL 形态必须携带非空 modelRef 与
 * selectionVersion；NONE 形态不得携带任何模型字段。默认模型不在可选列表中
 * 时以 NONE 表示——不会回退为其他模型。
 */
public record ModelCatalogDefaultSelection(
        Kind kind, String modelRef, String selectionVersion) {

    /** 默认选择形态：MODEL 指向某个可选模型，NONE 表示无默认模型。 */
    public enum Kind {
        MODEL,
        NONE
    }

    /** 构造 NONE 形态的默认选择。 */
    public static ModelCatalogDefaultSelection none() {
        return new ModelCatalogDefaultSelection(Kind.NONE, null, null);
    }

    /** 从已准入描述符投影出 MODEL 形态的默认选择。 */
    public static ModelCatalogDefaultSelection model(ModelProviderDescriptor descriptor) {
        return new ModelCatalogDefaultSelection(
                Kind.MODEL,
                descriptor.getModelRef().value(),
                descriptor.getSelectionVersion());
    }

    public ModelCatalogDefaultSelection {
        if (kind == null) {
            throw new IllegalArgumentException("selection kind is required");
        }
        if (kind == Kind.MODEL
                && (modelRef == null || modelRef.isBlank()
                || selectionVersion == null || selectionVersion.isBlank())) {
            throw new IllegalArgumentException("model default is incomplete");
        }
        if (kind == Kind.NONE && (modelRef != null || selectionVersion != null)) {
            throw new IllegalArgumentException("NONE default must not carry model fields");
        }
    }
}
