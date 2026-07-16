package com.portfolio.agent.answer.engine;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

@Component
public class QuestionNormalizer {

    private static final Set<Integer> TRAILING_PUNCTUATION = Set.of(
            (int) '?', (int) '!', (int) '.', (int) ';', (int) ',',
            (int) '？', (int) '！', (int) '。', (int) '；', (int) '，'
    );

    public String normalize(String question) {
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
