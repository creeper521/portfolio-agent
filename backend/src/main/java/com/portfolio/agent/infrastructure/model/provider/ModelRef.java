package com.portfolio.agent.infrastructure.model.provider;

import java.util.regex.Pattern;

/**
 * 模型稳定公开标识：小写 kebab-case 文本（如 {@code glm-4-flash}），
 * 长度不超过 64，可作为目录键与公开投影中的模型引用。
 *
 * <p>实现 {@code Comparable}，保证目录排序与快照指纹派生的确定性。
 */
public record ModelRef(String value) implements Comparable<ModelRef> {
    private static final Pattern FORMAT =
            Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    /** 紧凑构造器：格式或长度非法时抛出 IllegalArgumentException。 */
    public ModelRef {
        if (value == null || value.length() > 64 || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("model ref must be lower-case kebab text");
        }
    }

    /** 便捷工厂，语义等同于构造器。 */
    public static ModelRef of(String value) {
        return new ModelRef(value);
    }

    /** 按文本字典序比较，为目录排序提供稳定顺序。 */
    @Override
    public int compareTo(ModelRef other) {
        return value.compareTo(other.value);
    }
}
