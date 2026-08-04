package com.portfolio.agent.common.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

public final class StableQuestionNormalizer {

    private static final Set<Integer> TRAILING_PUNCTUATION = Set.of(
            0x003F, 0x0021, 0x002E, 0x003B, 0x002C,
            0xFF1F, 0xFF01, 0x3002, 0xFF1B, 0xFF0C);

    private StableQuestionNormalizer() {
    }

    public static String normalize(String question) {
        if (question == null) {
            return "";
        }
        String normalized = Normalizer.normalize(question, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .strip()
                .replaceAll("\\s+", " ");
        int end = normalized.length();
        while (end > 0) {
            int codePoint = normalized.codePointBefore(end);
            if (!TRAILING_PUNCTUATION.contains(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return normalized.substring(0, end);
    }
}
