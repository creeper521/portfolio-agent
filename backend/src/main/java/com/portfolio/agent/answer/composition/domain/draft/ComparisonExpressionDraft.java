package com.portfolio.agent.answer.composition.domain.draft;

import com.portfolio.agent.answer.composition.domain.MaterialKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ComparisonExpressionDraft extends ModelExpressionDraft {
    private final DraftText intro;
    private final List<ComparisonDraftDimension> dimensions;
    public ComparisonExpressionDraft(String schemaVersion, DraftText intro,
            List<ComparisonDraftDimension> dimensions) {
        super(schemaVersion, MaterialKind.COMPARISON);
        this.intro = Objects.requireNonNull(intro, "intro");
        this.dimensions = List.copyOf(Objects.requireNonNull(dimensions, "dimensions"));
        if (this.dimensions.isEmpty() || allBodySentences().size() > 18) {
            throw new IllegalArgumentException("comparison limit");
        }
    }
    public DraftText getIntro() { return intro; }
    public List<ComparisonDraftDimension> getDimensions() { return dimensions; }
    @Override public List<DraftSentence> allBodySentences() {
        List<DraftSentence> values = new ArrayList<>();
        dimensions.forEach(dimension -> {
            dimension.getSubjects().forEach(subject -> values.addAll(subject.getSentences()));
            values.addAll(dimension.getComparisonSentences());
        });
        return List.copyOf(values);
    }
    @Override public List<DraftText> introductoryTexts() { return List.of(intro); }

    public static final class ComparisonDraftDimension {
        private final String dimensionKey;
        private final List<ComparisonDraftSubject> subjects;
        private final List<DraftSentence> comparisonSentences;
        public ComparisonDraftDimension(String dimensionKey, List<ComparisonDraftSubject> subjects,
                List<DraftSentence> comparisonSentences) {
            if (dimensionKey == null || !dimensionKey.matches("D\\d{2}")) {
                throw new IllegalArgumentException("dimension alias invalid");
            }
            this.dimensionKey = dimensionKey;
            this.subjects = List.copyOf(Objects.requireNonNull(subjects, "subjects"));
            this.comparisonSentences = List.copyOf(
                    Objects.requireNonNull(comparisonSentences, "comparisonSentences"));
        }
        public String getDimensionKey() { return dimensionKey; }
        public List<ComparisonDraftSubject> getSubjects() { return subjects; }
        public List<DraftSentence> getComparisonSentences() { return comparisonSentences; }
    }
    public static final class ComparisonDraftSubject {
        private final String subjectKey;
        private final List<DraftSentence> sentences;
        public ComparisonDraftSubject(String subjectKey, List<DraftSentence> sentences) {
            if (subjectKey == null || !subjectKey.matches("P\\d{2}")) {
                throw new IllegalArgumentException("subject alias invalid");
            }
            this.subjectKey = subjectKey;
            this.sentences = List.copyOf(Objects.requireNonNull(sentences, "sentences"));
        }
        public String getSubjectKey() { return subjectKey; }
        public List<DraftSentence> getSentences() { return sentences; }
    }
}
