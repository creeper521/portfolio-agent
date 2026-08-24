package com.portfolio.agent.turn.planning;

import java.util.Locale;

public final class ConversationalMessageValidator {

    static final int MAX_CHARACTERS = 160;
    private static final int MIN_REPLAY_CHECK_CHARACTERS = 8;

    public String validate(String message, String userText) {
        if (message == null || message.isBlank()
                || message.length() > MAX_CHARACTERS) {
            throw new IllegalArgumentException(
                    "conversational message is required and bounded");
        }
        if (message.chars().anyMatch(value -> Character.isISOControl(value))) {
            throw new IllegalArgumentException(
                    "conversational message contains control characters");
        }

        long hanCharacters = message.codePoints()
                .filter(value -> Character.UnicodeScript.of(value)
                        == Character.UnicodeScript.HAN)
                .count();
        long latinLetters = message.codePoints()
                .filter(value -> Character.UnicodeScript.of(value)
                        == Character.UnicodeScript.LATIN)
                .count();
        if (hanCharacters < 2 || latinLetters > hanCharacters * 2) {
            throw new IllegalArgumentException(
                    "conversational message must be primarily Chinese");
        }
        if (message.matches(".*[A-Za-z][A-Za-z ,'-]{15,}[.!?].*")) {
            throw new IllegalArgumentException(
                    "conversational message contains a complete English sentence");
        }

        String normalizedUserText = normalize(userText);
        String normalizedMessage = normalize(message);
        if (normalizedUserText.length() >= MIN_REPLAY_CHECK_CHARACTERS
                && normalizedMessage.contains(normalizedUserText)) {
            throw new IllegalArgumentException(
                    "conversational message repeats visitor input");
        }
        return message;
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }
}
