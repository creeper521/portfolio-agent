package com.portfolio.agent.infrastructure.model.provider;

import java.util.regex.Pattern;

/** Stable public identifier for a configured model entry. */
public record ModelRef(String value) implements Comparable<ModelRef> {
    private static final Pattern FORMAT =
            Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    public ModelRef {
        if (value == null || value.length() > 64 || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("model ref must be lower-case kebab text");
        }
    }

    public static ModelRef of(String value) {
        return new ModelRef(value);
    }

    @Override
    public int compareTo(ModelRef other) {
        return value.compareTo(other.value);
    }
}
