package com.portfolio.agent.answer.domain;

import java.util.List;
import java.util.Objects;

public final class AnswerResult {

    private final AnswerMode answerMode;
    private final boolean matched;
    private final boolean fallback;
    private final String title;
    private final List<AnswerSection> sections;
    private final List<AnswerEvidence> evidence;
    private final List<String> suggestedQuestions;

    public AnswerResult(
            AnswerMode answerMode,
            boolean matched,
            boolean fallback,
            String title,
            List<AnswerSection> sections,
            List<AnswerEvidence> evidence,
            List<String> suggestedQuestions
    ) {
        this.answerMode = answerMode;
        this.matched = matched;
        this.fallback = fallback;
        this.title = title;
        this.sections = List.copyOf(sections);
        this.evidence = List.copyOf(evidence);
        this.suggestedQuestions = List.copyOf(suggestedQuestions);
    }

    public AnswerMode getAnswerMode() {
        return answerMode;
    }

    public boolean isMatched() {
        return matched;
    }

    public boolean isFallback() {
        return fallback;
    }

    public String getTitle() {
        return title;
    }

    public List<AnswerSection> getSections() {
        return sections;
    }

    public List<AnswerEvidence> getEvidence() {
        return evidence;
    }

    public List<String> getSuggestedQuestions() {
        return suggestedQuestions;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AnswerResult that)) {
            return false;
        }
        return matched == that.matched
                && fallback == that.fallback
                && Objects.equals(answerMode, that.answerMode)
                && Objects.equals(title, that.title)
                && Objects.equals(sections, that.sections)
                && Objects.equals(evidence, that.evidence)
                && Objects.equals(suggestedQuestions, that.suggestedQuestions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(answerMode, matched, fallback, title, sections, evidence,
                suggestedQuestions);
    }

    @Override
    public String toString() {
        return "AnswerResult{" +
                "answerMode=" + answerMode +
                ", matched=" + matched +
                ", fallback=" + fallback +
                ", title='" + title + '\'' +
                ", sections=" + sections +
                ", evidence=" + evidence +
                ", suggestedQuestions=" + suggestedQuestions +
                '}';
    }
}
