package com.portfolio.agent.answer.domain;

import com.portfolio.agent.answer.dto.request.AnswerRequestSource;
import com.portfolio.agent.answer.dto.request.AudienceRole;

import java.time.Instant;
import java.util.List;

public final class AnswerDecision {

    private final int eventVersion;
    private final String eventType;
    private final Instant occurredAt;
    private final String contentVersion;
    private final String projectSlug;
    private final QuestionKind questionKind;
    private final String questionPresetId;
    private final AudienceRole audienceRole;
    private final AnswerRequestSource source;
    private final AnswerResolution resolution;
    private final AnswerSource answerSource;
    private final GenerationMode generationMode;
    private final VerificationStatus verification;
    private final List<String> evidenceIds;
    private final DurationBucket durationBucket;
    private final String errorCode;

    public AnswerDecision(
            Instant occurredAt,
            AnswerTurnSnapshot turn,
            QuestionKind questionKind,
            AnswerResult result,
            DurationBucket durationBucket,
            String errorCode
    ) {
        this.eventVersion = 1;
        this.eventType = "ANSWER_DECIDED";
        this.occurredAt = occurredAt;
        this.contentVersion = turn.getContentVersion();
        this.projectSlug = turn.getProjectSlug();
        this.questionKind = questionKind;
        this.questionPresetId = turn.getQuestionPresetId();
        this.audienceRole = turn.getAudienceRole();
        this.source = turn.getSource();
        this.resolution = result.getResolution();
        this.answerSource = result.getAnswerSource();
        this.generationMode = result.getGenerationMode();
        this.verification = result.getVerification();
        this.evidenceIds = List.copyOf(result.getEvidenceIds());
        this.durationBucket = durationBucket;
        this.errorCode = errorCode;
    }

    public int getEventVersion() { return eventVersion; }
    public String getEventType() { return eventType; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getContentVersion() { return contentVersion; }
    public String getProjectSlug() { return projectSlug; }
    public QuestionKind getQuestionKind() { return questionKind; }
    public String getQuestionPresetId() { return questionPresetId; }
    public AudienceRole getAudienceRole() { return audienceRole; }
    public AnswerRequestSource getSource() { return source; }
    public AnswerResolution getResolution() { return resolution; }
    public AnswerSource getAnswerSource() { return answerSource; }
    public GenerationMode getGenerationMode() { return generationMode; }
    public VerificationStatus getVerification() { return verification; }
    public List<String> getEvidenceIds() { return evidenceIds; }
    public DurationBucket getDurationBucket() { return durationBucket; }
    public String getErrorCode() { return errorCode; }
}
