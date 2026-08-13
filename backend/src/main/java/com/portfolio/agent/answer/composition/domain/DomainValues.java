package com.portfolio.agent.answer.composition.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

final class DomainValues {
    private DomainValues() {}

    static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        String normalized = value.trim();
        if (normalized.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException(name + " contains a control character");
        }
        return normalized;
    }

    static String optionalText(String value, String name) {
        return value == null ? null : requireText(value, name);
    }

    static <T> List<T> distinctCopy(List<T> values, String name) {
        Objects.requireNonNull(values, name);
        List<T> result = new ArrayList<>();
        LinkedHashSet<T> seen = new LinkedHashSet<>();
        for (T value : values) {
            if (value == null || !seen.add(value)) {
                throw new IllegalArgumentException(name + " contains null or duplicate values");
            }
            result.add(value);
        }
        return List.copyOf(result);
    }

    static List<String> distinctTextCopy(List<String> values, String name) {
        Objects.requireNonNull(values, name);
        List<String> result = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = requireText(value, name);
            if (!seen.add(normalized)) {
                throw new IllegalArgumentException(name + " contains duplicate values");
            }
            result.add(normalized);
        }
        return List.copyOf(result);
    }
}
