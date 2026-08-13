package com.portfolio.agent.answer.composition.domain.draft;

import com.portfolio.agent.answer.composition.domain.MaterialKind;
import java.util.List;
import java.util.Objects;

public final class RecommendationExpressionDraft extends ModelExpressionDraft {
    private final DraftText intro;
    private final List<RecommendationDraftItem> items;
    public RecommendationExpressionDraft(String schemaVersion, DraftText intro,
            List<RecommendationDraftItem> items) {
        super(schemaVersion, MaterialKind.RECOMMENDATION);
        this.intro = Objects.requireNonNull(intro, "intro");
        this.items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (this.items.isEmpty() || allBodySentences().size() > 18) {
            throw new IllegalArgumentException("recommendation limit");
        }
    }
    public DraftText getIntro() { return intro; }
    public List<RecommendationDraftItem> getItems() { return items; }
    @Override public List<DraftSentence> allBodySentences() {
        return items.stream().flatMap(item -> item.getSentences().stream()).toList();
    }
    @Override public List<DraftText> introductoryTexts() { return List.of(intro); }

    public static final class RecommendationDraftItem {
        private final String candidateKey;
        private final List<DraftSentence> sentences;
        public RecommendationDraftItem(String candidateKey, List<DraftSentence> sentences) {
            if (candidateKey == null || !candidateKey.matches("C\\d{2}")) {
                throw new IllegalArgumentException("candidate alias invalid");
            }
            this.candidateKey = candidateKey;
            this.sentences = List.copyOf(Objects.requireNonNull(sentences, "sentences"));
        }
        public String getCandidateKey() { return candidateKey; }
        public List<DraftSentence> getSentences() { return sentences; }
    }
}
