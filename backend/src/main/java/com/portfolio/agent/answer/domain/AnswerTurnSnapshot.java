package com.portfolio.agent.answer.domain;

import com.portfolio.agent.answer.dto.request.AnswerRequestSource;
import com.portfolio.agent.answer.dto.request.AudienceRole;

import java.util.List;

public final class AnswerTurnSnapshot {

    private final String turnId;
    private final String requestId;
    private final String contentVersion;
    private final String runtimeBundleHash;
    private final String projectSlug;
    private final String questionPresetId;
    private final List<String> approvedEvidenceIds;
    private final AudienceRole audienceRole;
    private final AnswerRequestSource source;

    public AnswerTurnSnapshot(
            String turnId,
            String requestId,
            RuntimeAnswerContent content,
            QuestionResolution resolution,
            AudienceRole audienceRole,
            AnswerRequestSource source
    ) {
        this.turnId = turnId;
        this.requestId = requestId;
        this.contentVersion = content.getContentVersion();
        this.runtimeBundleHash = content.getRuntimeBundleHash();
        this.projectSlug = resolution.getProject().getSlug();
        this.questionPresetId = resolution.getQuestionPreset() == null
                ? null
                : resolution.getQuestionPreset().getId();
        this.approvedEvidenceIds = resolution.getResolution() == AnswerResolution.ANSWERED
                ? resolution.getProject().getEvidence().stream().map(AnswerEvidence::getId).toList()
                : List.of();
        this.audienceRole = audienceRole;
        this.source = source;
    }

    public AnswerTurnSnapshot(
            String turnId,
            String requestId,
            String contentVersion,
            String runtimeBundleHash,
            String projectSlug,
            String questionPresetId,
            List<String> approvedEvidenceIds,
            AudienceRole audienceRole,
            AnswerRequestSource source
    ) {
        this.turnId = turnId;
        this.requestId = requestId;
        this.contentVersion = contentVersion;
        this.runtimeBundleHash = runtimeBundleHash;
        this.projectSlug = projectSlug;
        this.questionPresetId = questionPresetId;
        this.approvedEvidenceIds = List.copyOf(approvedEvidenceIds);
        this.audienceRole = audienceRole;
        this.source = source;
    }

    public String getTurnId() { return turnId; }
    public String getRequestId() { return requestId; }
    public String getContentVersion() { return contentVersion; }
    public String getRuntimeBundleHash() { return runtimeBundleHash; }
    public String getProjectSlug() { return projectSlug; }
    public String getQuestionPresetId() { return questionPresetId; }
    public List<String> getApprovedEvidenceIds() { return approvedEvidenceIds; }
    public AudienceRole getAudienceRole() { return audienceRole; }
    public AnswerRequestSource getSource() { return source; }
}
