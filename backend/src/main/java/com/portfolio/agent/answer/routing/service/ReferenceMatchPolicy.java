package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.TextAnchor;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

/** The sole normalized anchor-boundary matcher for reviewed aliases and page-reference markers. */
public final class ReferenceMatchPolicy {

    public boolean matches(TextAnchor anchor, String currentInput, String alias) {
        Objects.requireNonNull(anchor, "anchor");
        if (currentInput == null || alias == null || alias.isBlank()) {
            return false;
        }
        TextAnchor.TextSpan span;
        try {
            span = anchor.resolveIn(currentInput);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        if (!normalize(span.getText()).equals(normalize(alias))) {
            return false;
        }
        return hasTokenBoundary(currentInput, span.getStartInclusive() - 1, -1)
                && hasTokenBoundary(currentInput, span.getEndExclusive(), 1);
    }

    private boolean hasTokenBoundary(String input, int index, int direction) {
        if (index < 0 || index >= input.length()) {
            return true;
        }
        int codePoint = input.codePointAt(index);
        if (direction < 0 && Character.isLowSurrogate(input.charAt(index))) {
            codePoint = input.codePointBefore(index + 1);
        }
        return !isAsciiWordCodePoint(codePoint);
    }

    private boolean isAsciiWordCodePoint(int codePoint) {
        return codePoint == '_' || codePoint >= '0' && codePoint <= '9'
                || codePoint >= 'A' && codePoint <= 'Z' || codePoint >= 'a' && codePoint <= 'z';
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).trim().toLowerCase(Locale.ROOT);
    }
}
