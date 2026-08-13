package com.portfolio.agent.answer.composition.domain.draft;

import com.portfolio.agent.answer.composition.domain.MaterialKind;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import java.util.List;
import java.util.Objects;

public final class FactExpressionDraft extends ModelExpressionDraft {
    private final DraftText summary;
    private final List<FactDraftSection> sections;
    public FactExpressionDraft(String schemaVersion, DraftText summary, List<FactDraftSection> sections) {
        super(schemaVersion, MaterialKind.FACT);
        this.summary = summary;
        this.sections = List.copyOf(Objects.requireNonNull(sections, "sections"));
        if (this.sections.isEmpty() || this.sections.size() > 6) {
            throw new IllegalArgumentException("fact section limit");
        }
        if (allBodySentences().size() > 18) throw new IllegalArgumentException("sentence limit");
    }
    public DraftText getSummary() { return summary; }
    public List<FactDraftSection> getSections() { return sections; }
    @Override public List<DraftSentence> allBodySentences() {
        return sections.stream().flatMap(section -> section.getSentences().stream()).toList();
    }
    @Override public List<DraftText> introductoryTexts() {
        return summary == null ? List.of() : List.of(summary);
    }

    public static final class FactDraftSection {
        private final AnswerSectionType sectionType;
        private final List<DraftSentence> sentences;
        public FactDraftSection(AnswerSectionType sectionType, List<DraftSentence> sentences) {
            this.sectionType = Objects.requireNonNull(sectionType, "sectionType");
            if (sectionType == AnswerSectionType.BOUNDARY || sectionType == AnswerSectionType.REJECTED) {
                throw new IllegalArgumentException("server-owned section");
            }
            this.sentences = List.copyOf(Objects.requireNonNull(sentences, "sentences"));
            if (this.sentences.isEmpty() || this.sentences.size() > 4) {
                throw new IllegalArgumentException("section sentence limit");
            }
        }
        public AnswerSectionType getSectionType() { return sectionType; }
        public List<DraftSentence> getSentences() { return sentences; }
    }
}
