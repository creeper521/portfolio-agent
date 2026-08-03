package com.portfolio.agent.answer.intelligence.service;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PortfolioTextRelevance {

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "for", "from",
            "how", "in", "is", "it", "of", "on", "or", "show", "that", "the",
            "this", "to", "was", "what", "when", "where", "which", "with");

    private PortfolioTextRelevance() {
    }

    public static boolean matches(String query, String content) {
        String normalizedContent = normalize(content);
        List<String> tokens = tokens(query);
        if (tokens.isEmpty()) {
            return false;
        }
        return tokens.stream().anyMatch(normalizedContent::contains);
    }

    private static List<String> tokens(String query) {
        String normalized = normalize(query);
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : normalized.split("[^\\p{L}\\p{N}]+")) {
            if (token.isBlank() || STOP_WORDS.contains(token)) {
                continue;
            }
            if (containsCjk(token)) {
                if (token.codePointCount(0, token.length()) <= 4) {
                    tokens.add(token);
                }
                List<Integer> codePoints = token.codePoints().boxed().toList();
                for (int index = 0; index + 1 < codePoints.size(); index++) {
                    tokens.add(new String(new int[]{
                            codePoints.get(index), codePoints.get(index + 1)}, 0, 2));
                }
            } else if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return List.copyOf(tokens);
    }

    private static boolean containsCjk(String value) {
        return value.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .strip();
    }
}
