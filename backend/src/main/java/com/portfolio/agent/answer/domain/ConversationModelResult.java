package com.portfolio.agent.answer.domain;

import java.util.Objects;

public final class ConversationModelResult<T> {

    private final T value;
    private final ConversationModelFailureCode failureCode;

    private ConversationModelResult(T value, ConversationModelFailureCode failureCode) {
        this.value = value;
        this.failureCode = failureCode;
    }

    public static <T> ConversationModelResult<T> success(T value) {
        return new ConversationModelResult<>(Objects.requireNonNull(value, "value"), null);
    }

    public static <T> ConversationModelResult<T> failure(
            ConversationModelFailureCode failureCode
    ) {
        return new ConversationModelResult<>(null, Objects.requireNonNull(
                failureCode, "failureCode"));
    }

    public boolean isSuccessful() {
        return value != null;
    }

    public T getValue() {
        return value;
    }

    public ConversationModelFailureCode getFailureCode() {
        return failureCode;
    }
}
