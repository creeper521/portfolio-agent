package com.portfolio.agent.turn.continuation;

import java.util.Objects;
import java.util.Optional;

/**
 * Backend-owned pointer generation guard and mutation carried into atomic
 * settlement. Null expectedGeneration means "expect no pointer", not wildcard.
 *
 * <p>讨论状态变更：进入 Settlement 的指针代数守卫与变更。GUARD 只校验
 * 当前指针代；REPLACE 替换指针；CLEAR 清除指针；NONE 无操作。
 * expectedGeneration 为 null 表示"期望当前没有指针"，不是通配。</p>
 */
public final class DiscussionStateMutation {
    private final Kind kind;
    private final String expectedGeneration;
    private final ActiveDiscussionPointer replacement;

    private DiscussionStateMutation(
            Kind kind,
            String expectedGeneration,
            ActiveDiscussionPointer replacement) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.expectedGeneration = expectedGeneration;
        this.replacement = replacement;
        if (kind == Kind.REPLACE && replacement == null
                || kind != Kind.REPLACE && replacement != null
                || kind == Kind.GUARD && expectedGeneration == null
                || kind == Kind.CLEAR && expectedGeneration == null) {
            throw new IllegalArgumentException(
                    "discussion state mutation shape is invalid");
        }
    }

    /** 无操作单例。 */
    public static DiscussionStateMutation none() {
        return new DiscussionStateMutation(Kind.NONE, null, null);
    }

    /** 构造只读代数守卫（要求当前指针为期望代）。 */
    public static DiscussionStateMutation guard(String expectedGeneration) {
        return new DiscussionStateMutation(
                Kind.GUARD, text(expectedGeneration), null);
    }

    /** 构造指针替换变更；expectedGeneration 为 null 表示期望当前无指针。 */
    public static DiscussionStateMutation replace(
            String expectedGeneration,
            ActiveDiscussionPointer replacement) {
        return new DiscussionStateMutation(
                Kind.REPLACE,
                expectedGeneration == null ? null : text(expectedGeneration),
                Objects.requireNonNull(replacement, "replacement"));
    }

    /** 构造指针清除变更（要求当前指针为期望代）。 */
    public static DiscussionStateMutation clear(String expectedGeneration) {
        return new DiscussionStateMutation(
                Kind.CLEAR, text(expectedGeneration), null);
    }

    public Kind getKind() { return kind; }
    public Optional<String> getExpectedGeneration() {
        return Optional.ofNullable(expectedGeneration);
    }
    public Optional<ActiveDiscussionPointer> getReplacement() {
        return Optional.ofNullable(replacement);
    }
    public boolean isNone() { return kind == Kind.NONE; }

    /** 判断当前指针是否满足期望代；NONE 恒真，null 期望要求当前无指针。 */
    public boolean matches(ActiveDiscussionPointer current) {
        if (kind == Kind.NONE) return true;
        if (expectedGeneration == null) return current == null;
        return current != null
                && current.matchesGeneration(expectedGeneration);
    }

    /** 计算变更后的指针：NONE/GUARD 保持原指针，REPLACE 换新，CLEAR 置空。 */
    public ActiveDiscussionPointer result(
            ActiveDiscussionPointer current) {
        return switch (kind) {
            case NONE, GUARD -> current;
            case REPLACE -> replacement;
            case CLEAR -> null;
        };
    }

    private static String text(String value) {
        return ContinuationContext.text(value, "expectedGeneration");
    }

    /** 变更类别：无操作/只读守卫/替换指针/清除指针。 */
    public enum Kind { NONE, GUARD, REPLACE, CLEAR }
}
