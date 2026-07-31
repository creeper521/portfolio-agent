package com.portfolio.agent.answer.domain;

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
                newProgress);
    }
}
