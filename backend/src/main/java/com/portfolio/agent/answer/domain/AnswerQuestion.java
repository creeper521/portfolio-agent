package com.portfolio.agent.answer.domain;

import java.util.List;
import java.util.Objects;

public final class AnswerQuestion {

    private final String id;
    private final String canonicalQuestion;
    private final List<String> aliases;
    private final String suggestion;
    private final List<AnswerClaimCategory> preferredClaimCategories;

    public AnswerQuestion(
            String id,
            String canonicalQuestion,
            List<String> aliases,
            String suggestion,
            List<AnswerClaimCategory> preferredClaimCategories
    ) {
        this.id = id;
        this.canonicalQuestion = canonicalQuestion;
        this.aliases = List.copyOf(aliases);
        this.suggestion = suggestion;
        this.preferredClaimCategories =
                List.copyOf(preferredClaimCategories);
    }

    public AnswerQuestion(
            String id,
            String canonicalQuestion,
            List<String> aliases,
            String suggestion
    ) {
        this(id, canonicalQuestion, aliases, suggestion, List.of());
    }

    public AnswerQuestion(String canonicalQuestion, List<String> aliases, String suggestion) {
        this("legacy-preset", canonicalQuestion, aliases, suggestion, List.of());
    }

    public String getId() {
        return id;
    }

    public String getCanonicalQuestion() {
        return canonicalQuestion;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public List<AnswerClaimCategory> getPreferredClaimCategories() {
        return preferredClaimCategories;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AnswerQuestion that)) {
            return false;
        }
        return Objects.equals(id, that.id)
                && Objects.equals(canonicalQuestion, that.canonicalQuestion)
                && Objects.equals(aliases, that.aliases)
                && Objects.equals(suggestion, that.suggestion)
                && Objects.equals(
                        preferredClaimCategories,
                        that.preferredClaimCategories);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                canonicalQuestion,
                aliases,
                suggestion,
                preferredClaimCategories);
    }

    @Override
    public String toString() {
        return "AnswerQuestion{" +
                "id='" + id + '\'' +
                ", canonicalQuestion='" + canonicalQuestion + '\'' +
                ", aliases=" + aliases +
                ", suggestion='" + suggestion + '\'' +
                ", preferredClaimCategories=" + preferredClaimCategories +
                '}';
    }
}
