package com.portfolio.agent.turn.continuation;

import java.util.Objects;
import java.util.Optional;

/**
 * Backend-owned pointer generation guard and mutation carried into atomic
 * settlement. Null expectedGeneration means "expect no pointer", not wildcard.
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

    public static DiscussionStateMutation none() {
        return new DiscussionStateMutation(Kind.NONE, null, null);
    }

    public static DiscussionStateMutation guard(String expectedGeneration) {
        return new DiscussionStateMutation(
                Kind.GUARD, text(expectedGeneration), null);
    }

    public static DiscussionStateMutation replace(
            String expectedGeneration,
            ActiveDiscussionPointer replacement) {
        return new DiscussionStateMutation(
                Kind.REPLACE,
                expectedGeneration == null ? null : text(expectedGeneration),
                Objects.requireNonNull(replacement, "replacement"));
    }

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

    public boolean matches(ActiveDiscussionPointer current) {
        if (kind == Kind.NONE) return true;
        if (expectedGeneration == null) return current == null;
        return current != null
                && current.matchesGeneration(expectedGeneration);
    }

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

    public enum Kind { NONE, GUARD, REPLACE, CLEAR }
}
