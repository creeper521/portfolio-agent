package com.portfolio.agent.answer.domain;

import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendation;
import com.portfolio.agent.answer.intelligence.domain.AnswerIntentSource;
import java.util.List;
import java.util.Objects;

public final class ConversationAnswerResult {

    private final String turnId;
    private final String contentVersion;
    private final ConversationIntent intent;
    private final ConversationAnswerScope answerScope;
    private final AnswerResolution resolution;
    private final String title;
    private final List<ConversationAnswerBlock> blocks;
    private final List<ConversationSuggestedQuestion> suggestedQuestions;
    private final boolean degraded;
    private final GenerationMode generationMode;
    private final AnswerSource answerSource;
    private final String noticeCode;
    private final ConversationProgress progress;
    private final PortfolioRecommendation portfolioRecommendation;
    private final AnswerConstructionMode constructionMode;
    private final AnswerIntentSource intentSource;
    private final AnswerEvidenceState evidenceState;
    private final boolean contextVersionUpdated;
    private final String questionPresetId;
    private final String contractVersion;
    private final String summary;

    public ConversationAnswerResult(
            String turnId,
            String contentVersion,
            ConversationIntent intent,
            ConversationAnswerScope answerScope,
            AnswerResolution resolution,
            String title,
            List<ConversationAnswerBlock> blocks,
            List<ConversationSuggestedQuestion> suggestedQuestions,
            boolean degraded
    ) {
        this(turnId, contentVersion, intent, answerScope, resolution, title,
                blocks, suggestedQuestions, degraded,
                degraded ? GenerationMode.FALLBACK : GenerationMode.DETERMINISTIC,
                null,
                degraded ? "MODEL_UNAVAILABLE_FALLBACK" : null,
                new ConversationProgress(
                        List.of(),
                        ConversationGuidanceStage.OPENING));
    }

    public ConversationAnswerResult(
            String turnId,
            String contentVersion,
            ConversationIntent intent,
            ConversationAnswerScope answerScope,
            AnswerResolution resolution,
            String title,
            List<ConversationAnswerBlock> blocks,
            List<ConversationSuggestedQuestion> suggestedQuestions,
            boolean degraded,
            GenerationMode generationMode,
            AnswerSource answerSource,
            String noticeCode
    ) {
        this(turnId, contentVersion, intent, answerScope, resolution, title,
                blocks, suggestedQuestions, degraded, generationMode,
                answerSource, noticeCode, new ConversationProgress(
                        List.of(),
                        ConversationGuidanceStage.OPENING));
    }

    public ConversationAnswerResult(
            String turnId,
            String contentVersion,
            ConversationIntent intent,
            ConversationAnswerScope answerScope,
            AnswerResolution resolution,
            String title,
            List<ConversationAnswerBlock> blocks,
            List<ConversationSuggestedQuestion> suggestedQuestions,
            boolean degraded,
            GenerationMode generationMode,
            AnswerSource answerSource,
            String noticeCode,
            ConversationProgress progress
    ) {
        this(turnId, contentVersion, intent, answerScope, resolution, title, blocks,
                suggestedQuestions, degraded, generationMode, answerSource, noticeCode,
                progress, null);
    }

    public ConversationAnswerResult(
            String turnId,
            String contentVersion,
            ConversationIntent intent,
            ConversationAnswerScope answerScope,
            AnswerResolution resolution,
            String title,
            List<ConversationAnswerBlock> blocks,
            List<ConversationSuggestedQuestion> suggestedQuestions,
            boolean degraded,
            GenerationMode generationMode,
            AnswerSource answerSource,
            String noticeCode,
            ConversationProgress progress,
            PortfolioRecommendation portfolioRecommendation
    ) {
        this(
                turnId,
                contentVersion,
                intent,
                answerScope,
                resolution,
                title,
                blocks,
                suggestedQuestions,
                degraded,
                generationMode,
                answerSource,
                noticeCode,
                progress,
                portfolioRecommendation,
                deriveConstructionMode(generationMode, answerSource),
                deriveIntentSource(answerSource),
                deriveEvidenceState(answerScope, resolution, blocks));
    }

    public ConversationAnswerResult(
            String turnId,
            String contentVersion,
            ConversationIntent intent,
            ConversationAnswerScope answerScope,
            AnswerResolution resolution,
            String title,
            List<ConversationAnswerBlock> blocks,
            List<ConversationSuggestedQuestion> suggestedQuestions,
            boolean degraded,
            GenerationMode generationMode,
            AnswerSource answerSource,
            String noticeCode,
            ConversationProgress progress,
            PortfolioRecommendation portfolioRecommendation,
            AnswerConstructionMode constructionMode,
            AnswerIntentSource intentSource,
            AnswerEvidenceState evidenceState
    ) {
        this(turnId, contentVersion, intent, answerScope, resolution, title, blocks,
                suggestedQuestions, degraded, generationMode, answerSource, noticeCode,
                progress, portfolioRecommendation, constructionMode, intentSource,
                evidenceState, false, null, null, null);
    }

    private ConversationAnswerResult(
            String turnId,
            String contentVersion,
            ConversationIntent intent,
            ConversationAnswerScope answerScope,
            AnswerResolution resolution,
            String title,
            List<ConversationAnswerBlock> blocks,
            List<ConversationSuggestedQuestion> suggestedQuestions,
            boolean degraded,
            GenerationMode generationMode,
            AnswerSource answerSource,
            String noticeCode,
            ConversationProgress progress,
            PortfolioRecommendation portfolioRecommendation,
            AnswerConstructionMode constructionMode,
            AnswerIntentSource intentSource,
            AnswerEvidenceState evidenceState,
            boolean contextVersionUpdated,
            String questionPresetId,
            String contractVersion,
            String summary
    ) {
        this.turnId = Objects.requireNonNull(turnId, "turnId");
        this.contentVersion = Objects.requireNonNull(contentVersion, "contentVersion");
        this.intent = Objects.requireNonNull(intent, "intent");
        this.answerScope = Objects.requireNonNull(answerScope, "answerScope");
        this.resolution = Objects.requireNonNull(resolution, "resolution");
        this.title = Objects.requireNonNull(title, "title");
        this.blocks = List.copyOf(blocks);
        this.suggestedQuestions = List.copyOf(suggestedQuestions);
        this.degraded = degraded;
        this.generationMode = Objects.requireNonNull(generationMode, "generationMode");
        this.answerSource = answerSource;
        this.noticeCode = noticeCode;
        this.progress = Objects.requireNonNull(progress, "progress");
        this.portfolioRecommendation = portfolioRecommendation;
        this.constructionMode = Objects.requireNonNull(
                constructionMode, "constructionMode");
        this.intentSource = Objects.requireNonNull(intentSource, "intentSource");
        this.evidenceState = Objects.requireNonNull(evidenceState, "evidenceState");
        this.contextVersionUpdated = contextVersionUpdated;
        this.questionPresetId = normalizeNullable(questionPresetId);
        this.contractVersion = normalizeNullable(contractVersion);
        this.summary = normalizeNullable(summary);
    }

    public String getTurnId() { return turnId; }
    public String getContentVersion() { return contentVersion; }
    public ConversationIntent getIntent() { return intent; }
    public ConversationAnswerScope getAnswerScope() { return answerScope; }
    public AnswerResolution getResolution() { return resolution; }
    public String getTitle() { return title; }
    public List<ConversationAnswerBlock> getBlocks() { return blocks; }
    public List<ConversationSuggestedQuestion> getSuggestedQuestions() {
        return suggestedQuestions;
    }
    public boolean isDegraded() { return degraded; }
    public GenerationMode getGenerationMode() { return generationMode; }
    public AnswerSource getAnswerSource() { return answerSource; }
    public String getNoticeCode() { return noticeCode; }
    public ConversationProgress getProgress() { return progress; }
    public PortfolioRecommendation getPortfolioRecommendation() { return portfolioRecommendation; }
    public AnswerConstructionMode getConstructionMode() { return constructionMode; }
    public AnswerIntentSource getIntentSource() { return intentSource; }
    public AnswerEvidenceState getEvidenceState() { return evidenceState; }
    public boolean isContextVersionUpdated() { return contextVersionUpdated; }
    public String getQuestionPresetId() { return questionPresetId; }
    public String getContractVersion() { return contractVersion; }
    public String getSummary() { return summary; }

    public ConversationAnswerResult withGuidance(
            List<ConversationSuggestedQuestion> questions,
            ConversationProgress newProgress
    ) {
        return new ConversationAnswerResult(
                turnId,
                contentVersion,
                intent,
                answerScope,
                resolution,
                title,
                blocks,
                questions,
                degraded,
                generationMode,
                answerSource,
                noticeCode,
                newProgress,
                portfolioRecommendation,
                constructionMode,
                intentSource,
                evidenceState,
                contextVersionUpdated,
                questionPresetId,
                contractVersion,
                summary);
    }

    public ConversationAnswerResult withContextVersionUpdated(boolean updated) {
        return new ConversationAnswerResult(
                turnId, contentVersion, intent, answerScope, resolution, title, blocks,
                suggestedQuestions, degraded, generationMode, answerSource, noticeCode,
                progress, portfolioRecommendation, constructionMode, intentSource,
                evidenceState, updated, questionPresetId, contractVersion, summary);
    }

    public ConversationAnswerResult withContractIdentity(String presetId, String version) {
        return new ConversationAnswerResult(
                turnId, contentVersion, intent, answerScope, resolution, title, blocks,
                suggestedQuestions, degraded, generationMode, answerSource, noticeCode,
                progress, portfolioRecommendation, constructionMode, intentSource,
                evidenceState, contextVersionUpdated, presetId, version, summary);
    }

    public ConversationAnswerResult withSummary(String newSummary) {
        return new ConversationAnswerResult(
                turnId, contentVersion, intent, answerScope, resolution, title, blocks,
                suggestedQuestions, degraded, generationMode, answerSource, noticeCode,
                progress, portfolioRecommendation, constructionMode, intentSource,
                evidenceState, contextVersionUpdated, questionPresetId, contractVersion,
                newSummary);
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static AnswerConstructionMode deriveConstructionMode(
            GenerationMode mode,
            AnswerSource source
    ) {
        if (mode == GenerationMode.MODEL) {
            return source == null
                    ? AnswerConstructionMode.GENERAL_MODEL
                    : AnswerConstructionMode.MODEL_GROUNDED;
        }
        return source == null
                ? AnswerConstructionMode.TEMPLATE
                : AnswerConstructionMode.EVIDENCE_COMPOSITION;
    }

    private static AnswerIntentSource deriveIntentSource(AnswerSource source) {
        if (source == AnswerSource.PRESET) {
            return AnswerIntentSource.PRESET;
        }
        return source == null ? AnswerIntentSource.GLOBAL : AnswerIntentSource.RULE;
    }

    private static AnswerEvidenceState deriveEvidenceState(
            ConversationAnswerScope scope,
            AnswerResolution resolution,
            List<ConversationAnswerBlock> blocks
    ) {
        if (resolution == AnswerResolution.NOT_SUPPORTED) {
            return AnswerEvidenceState.INSUFFICIENT;
        }
        if (blocks.stream().anyMatch(block -> !block.getEvidenceIds().isEmpty()
                || !block.getSourceReferences().isEmpty())) {
            return AnswerEvidenceState.VERIFIED;
        }
        if (scope == ConversationAnswerScope.PORTFOLIO
                || scope == ConversationAnswerScope.HYBRID
                || scope == ConversationAnswerScope.MIXED) {
            return AnswerEvidenceState.INSUFFICIENT;
        }
        return AnswerEvidenceState.NOT_REQUIRED;
    }
}
