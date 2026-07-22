package com.portfolio.agent.answer.domain;

import com.portfolio.agent.answer.dto.request.AudienceRole;

import java.util.List;

public final class AnswerPlan {

    private final String contentVersion;
    private final String questionPresetId;
    private final String canonicalIntent;
    private final AudienceRole audienceRole;
    private final String projectTitle;
    private final String projectSummary;
    private final List<AnswerPlanSection> requiredSections;
    private final List<AnswerPlanClaim> claims;
    private final List<AnswerPlanEvidence> evidence;
    private final ExpressionPolicy expressionPolicy;

    public AnswerPlan(
            String contentVersion,
            String questionPresetId,
            String canonicalIntent,
            AudienceRole audienceRole,
            String projectTitle,
            String projectSummary,
            List<AnswerPlanSection> requiredSections,
            List<AnswerPlanClaim> claims,
            List<AnswerPlanEvidence> evidence,
            ExpressionPolicy expressionPolicy
    ) {
        this.contentVersion = contentVersion;
        this.questionPresetId = questionPresetId;
        this.canonicalIntent = canonicalIntent;
        this.audienceRole = audienceRole;
        this.projectTitle = projectTitle;
        this.projectSummary = projectSummary;
        this.requiredSections = List.copyOf(requiredSections);
        this.claims = List.copyOf(claims);
        this.evidence = List.copyOf(evidence);
        this.expressionPolicy = expressionPolicy;
    }

    public String getContentVersion() { return contentVersion; }
    public String getQuestionPresetId() { return questionPresetId; }
    public String getCanonicalIntent() { return canonicalIntent; }
    public AudienceRole getAudienceRole() { return audienceRole; }
    public String getProjectTitle() { return projectTitle; }
    public String getProjectSummary() { return projectSummary; }
    public List<AnswerPlanSection> getRequiredSections() { return requiredSections; }
    public List<AnswerPlanClaim> getClaims() { return claims; }
    public List<AnswerPlanEvidence> getEvidence() { return evidence; }
    public ExpressionPolicy getExpressionPolicy() { return expressionPolicy; }
}
