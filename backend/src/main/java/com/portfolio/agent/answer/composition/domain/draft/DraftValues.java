package com.portfolio.agent.answer.composition.domain.draft;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

final class DraftValues {
    private static final Pattern FORBIDDEN = Pattern.compile(
            "(?is)(^|\\s)#{1,6}\\s|`|<[^>]+>|(?:^|\\s)>\\s|"
            + "(?:^|\\s)(?:[-*+]|\\d+\\.)\\s|[*_~]{2}|"
            + "[a-z][a-z0-9+.-]*:[^\\s]+|www\\.|!\\[[^]]*]\\(|\\[[^]]+]\\([^)]*\\)");
    private DraftValues() {}
    static String text(String value) {
        if (value == null || value.isBlank() || value.length() > 600
                || value.contains("\n") || value.contains("\r") || FORBIDDEN.matcher(value).find()) {
            throw new IllegalArgumentException("draft text invalid");
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character)) {
                throw new IllegalArgumentException("draft text contains control character");
            }
        }
        return value.trim();
    }
    static List<String> supports(List<String> values) {
        Objects.requireNonNull(values, "supports");
        if (values.isEmpty() || values.size() > 4) throw new IllegalArgumentException("supports limit");
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || !value.matches("S\\d{3}") || !result.add(value)) {
                throw new IllegalArgumentException("invalid support alias");
            }
        }
        return List.copyOf(result);
    }
}
