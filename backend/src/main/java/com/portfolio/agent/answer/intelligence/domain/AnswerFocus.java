package com.portfolio.agent.answer.intelligence.domain;

import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import java.util.List;
import java.util.Objects;

public final class AnswerFocus {

    private final AnswerFocusMode mode;
    private final List<AnswerClaimCategory> requestedClaimCategories;

    private AnswerFocus(
            AnswerFocusMode mode,
            List<AnswerClaimCategory> requestedClaimCategories) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.requestedClaimCategories = List.copyOf(
                Objects.requireNonNull(requestedClaimCategories, "requestedClaimCategories"));
    }

    public static AnswerFocus overview() {
        return new AnswerFocus(AnswerFocusMode.OVERVIEW, List.of());
    }

    public static AnswerFocus focused(List<AnswerClaimCategory> categories) {
        Objects.requireNonNull(categories, "categories");
        List<AnswerClaimCategory> distinct = categories.stream().distinct().toList();
        if (distinct.isEmpty()) {
            throw new IllegalArgumentException("focused answer requires claim categories");
        }
        return new AnswerFocus(AnswerFocusMode.FOCUSED, distinct);
    }

    public AnswerFocusMode getMode() { return mode; }
    public List<AnswerClaimCategory> getRequestedClaimCategories() {
        return requestedClaimCategories;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof AnswerFocus that)) { return false; }
        return mode == that.mode
                && Objects.equals(requestedClaimCategories, that.requestedClaimCategories);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, requestedClaimCategories);
    }

    @Override
    public String toString() {
        return "AnswerFocus{mode=" + mode + '}';
    }
}
