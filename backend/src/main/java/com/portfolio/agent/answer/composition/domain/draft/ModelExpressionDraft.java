package com.portfolio.agent.answer.composition.domain.draft;

import com.portfolio.agent.answer.composition.domain.MaterialKind;
import java.util.List;
import java.util.Objects;

public abstract sealed class ModelExpressionDraft
        permits FactExpressionDraft, ComparisonExpressionDraft, RecommendationExpressionDraft {
    public static final String SCHEMA_VERSION = "portfolio-expression-draft.v1";
    private final String schemaVersion;
    private final MaterialKind materialKind;

    protected ModelExpressionDraft(String schemaVersion, MaterialKind materialKind) {
        if (!SCHEMA_VERSION.equals(schemaVersion)) throw new IllegalArgumentException("schema mismatch");
        this.schemaVersion = schemaVersion;
        this.materialKind = Objects.requireNonNull(materialKind, "materialKind");
    }

    public String getSchemaVersion() { return schemaVersion; }
    public MaterialKind getMaterialKind() { return materialKind; }
    public abstract List<DraftSentence> allBodySentences();
    public abstract List<DraftText> introductoryTexts();
}
