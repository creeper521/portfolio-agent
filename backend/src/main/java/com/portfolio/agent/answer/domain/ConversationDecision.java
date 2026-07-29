package com.portfolio.agent.answer.domain;

import java.time.Instant;
import java.util.Objects;

public final class ConversationDecision {

    private final Instant occurredAt;
    private final String contentVersion;
    private final ConversationIntent intent;
    private final ConversationAnswerScope answerScope;
    private final AnswerResolution resolution;
    private final boolean degraded;
    private final GenerationMode generationMode;
    private final AnswerSource answerSource;
    private final DurationBucket durationBucket;

    public ConversationDecision(
            Instant occurredAt,
            String contentVersion,
            ConversationIntent intent,
            ConversationAnswerScope answerScope,
            AnswerResolution resolution,
            boolean degraded,
            GenerationMode generationMode,
            AnswerSource answerSource,
            DurationBucket durationBucket
    ) {
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.contentVersion = Objects.requireNonNull(contentVersion, "contentVersion");
        this.intent = Objects.requireNonNull(intent, "intent");
        this.answerScope = Objects.requireNonNull(answerScope, "answerScope");
        this.resolution = Objects.requireNonNull(resolution, "resolution");
        this.degraded = degraded;
        this.generationMode = Objects.requireNonNull(generationMode, "generationMode");
        this.answerSource = answerSource;
        this.durationBucket = Objects.requireNonNull(durationBucket, "durationBucket");
    }

    public Instant getOccurredAt() { return occurredAt; }
    public String getContentVersion() { return contentVersion; }
    public ConversationIntent getIntent() { return intent; }
    public ConversationAnswerScope getAnswerScope() { return answerScope; }
    public AnswerResolution getResolution() { return resolution; }
    public boolean isDegraded() { return degraded; }
    public GenerationMode getGenerationMode() { return generationMode; }
    public AnswerSource getAnswerSource() { return answerSource; }
    public DurationBucket getDurationBucket() { return durationBucket; }
}
