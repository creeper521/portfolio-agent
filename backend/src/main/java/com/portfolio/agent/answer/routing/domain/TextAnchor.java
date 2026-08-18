package com.portfolio.agent.answer.routing.domain;

import java.util.Objects;

/** A provider-proposed exact fragment and one-based non-overlapping occurrence in the current input. */
public final class TextAnchor {

    private static final int MAX_VERBATIM_LENGTH = 256;

    private final String verbatimText;
    private final int occurrence;

    public TextAnchor(String verbatimText, int occurrence) {
        if (verbatimText == null || verbatimText.isBlank()) {
            throw new IllegalArgumentException("verbatimText is required");
        }
        if (verbatimText.length() > MAX_VERBATIM_LENGTH) {
            throw new IllegalArgumentException("verbatimText exceeds the supported length");
        }
        if (occurrence < 1) {
            throw new IllegalArgumentException("occurrence must be positive");
        }
        this.verbatimText = verbatimText;
        this.occurrence = occurrence;
    }

    public String getVerbatimText() {
        return verbatimText;
    }

    public int getOccurrence() {
        return occurrence;
    }

    public TextSpan resolveIn(String currentInput) {
        if (currentInput == null) {
            throw new IllegalArgumentException("currentInput is required");
        }
        int searchFrom = 0;
        for (int index = 1; index <= occurrence; index++) {
            int match = currentInput.indexOf(verbatimText, searchFrom);
            if (match < 0) {
                throw new IllegalArgumentException("anchor occurrence is absent from currentInput");
            }
            int endExclusive = match + verbatimText.length();
            if (index == occurrence) {
                return new TextSpan(match, endExclusive, verbatimText, currentInput);
            }
            searchFrom = endExclusive;
        }
        throw new IllegalArgumentException("anchor occurrence is absent from currentInput");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TextAnchor that)) return false;
        return occurrence == that.occurrence && Objects.equals(verbatimText, that.verbatimText);
    }

    @Override
    public int hashCode() { return Objects.hash(verbatimText, occurrence); }

    @Override
    public String toString() {
        return "TextAnchor{length=" + verbatimText.length() + ", occurrence=" + occurrence + '}';
    }

    public static final class TextSpan {

        private final int startInclusive;
        private final int endExclusive;
        private final String text;

        private TextSpan(int startInclusive, int endExclusive, String text, String currentInput) {
            if (startInclusive < 0 || endExclusive <= startInclusive || endExclusive > currentInput.length()
                    || !currentInput.substring(startInclusive, endExclusive).equals(text)) {
                throw new IllegalArgumentException("text span is invalid");
            }
            this.startInclusive = startInclusive;
            this.endExclusive = endExclusive;
            this.text = text;
        }

        public int getStartInclusive() {
            return startInclusive;
        }

        public int getEndExclusive() {
            return endExclusive;
        }

        public String getText() {
            return text;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TextSpan that)) {
                return false;
            }
            return startInclusive == that.startInclusive && endExclusive == that.endExclusive
                    && Objects.equals(text, that.text);
        }

        @Override
        public int hashCode() {
            return Objects.hash(startInclusive, endExclusive, text);
        }
    }
}
