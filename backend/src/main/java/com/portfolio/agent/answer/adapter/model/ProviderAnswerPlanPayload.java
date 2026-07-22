package com.portfolio.agent.answer.adapter.model;

import com.portfolio.agent.answer.domain.AnswerPlan;
import com.portfolio.agent.answer.domain.AnswerPlanClaim;
import com.portfolio.agent.answer.domain.AnswerPlanEvidence;
import com.portfolio.agent.answer.domain.AnswerPlanSection;

import java.time.LocalDate;
import java.util.List;

final class ProviderAnswerPlanPayload {

    private final String contentVersion;
    private final String questionPresetId;
    private final String canonicalIntent;
    private final String audienceRole;
    private final String projectTitle;
    private final String projectSummary;
    private final List<SectionPayload> requiredSections;
    private final List<ClaimPayload> claims;
    private final List<EvidencePayload> evidence;
    private final ExpressionPolicyPayload expressionPolicy;

    ProviderAnswerPlanPayload(AnswerPlan plan) {
        this.contentVersion = plan.getContentVersion();
        this.questionPresetId = plan.getQuestionPresetId();
        this.canonicalIntent = plan.getCanonicalIntent();
        this.audienceRole = plan.getAudienceRole().name();
        this.projectTitle = plan.getProjectTitle();
        this.projectSummary = plan.getProjectSummary();
        this.requiredSections = plan.getRequiredSections().stream()
                .map(SectionPayload::new)
                .toList();
        this.claims = plan.getClaims().stream().map(ClaimPayload::new).toList();
        this.evidence = plan.getEvidence().stream().map(EvidencePayload::new).toList();
        this.expressionPolicy = new ExpressionPolicyPayload(plan);
    }

    public String getContentVersion() { return contentVersion; }
    public String getQuestionPresetId() { return questionPresetId; }
    public String getCanonicalIntent() { return canonicalIntent; }
    public String getAudienceRole() { return audienceRole; }
    public String getProjectTitle() { return projectTitle; }
    public String getProjectSummary() { return projectSummary; }
    public List<SectionPayload> getRequiredSections() { return requiredSections; }
    public List<ClaimPayload> getClaims() { return claims; }
    public List<EvidencePayload> getEvidence() { return evidence; }
    public ExpressionPolicyPayload getExpressionPolicy() { return expressionPolicy; }

    static final class SectionPayload {
        private final String type;
        private final String canonicalTitle;
        private final String canonicalContent;
        private final List<String> allowedClaimIds;
        private final List<String> allowedEvidenceIds;

        private SectionPayload(AnswerPlanSection section) {
            this.type = section.getType().name();
            this.canonicalTitle = section.getCanonicalTitle();
            this.canonicalContent = section.getCanonicalContent();
            this.allowedClaimIds = section.getAllowedClaimIds();
            this.allowedEvidenceIds = section.getAllowedEvidenceIds();
        }

        public String getType() { return type; }
        public String getCanonicalTitle() { return canonicalTitle; }
        public String getCanonicalContent() { return canonicalContent; }
        public List<String> getAllowedClaimIds() { return allowedClaimIds; }
        public List<String> getAllowedEvidenceIds() { return allowedEvidenceIds; }
    }

    static final class ClaimPayload {
        private final String claimId;
        private final String category;
        private final String statement;
        private final String detail;
        private final String achievementStatus;
        private final String contributionType;
        private final String verificationBasis;
        private final String materiality;
        private final List<String> allowedEvidenceIds;

        private ClaimPayload(AnswerPlanClaim claim) {
            this.claimId = claim.getClaimId();
            this.category = claim.getCategory().name();
            this.statement = claim.getStatement();
            this.detail = claim.getDetail();
            this.achievementStatus = claim.getAchievementStatus().name();
            this.contributionType = claim.getContributionType().name();
            this.verificationBasis = claim.getVerificationBasis().name();
            this.materiality = claim.getMateriality().name();
            this.allowedEvidenceIds = claim.getAllowedEvidenceIds();
        }

        public String getClaimId() { return claimId; }
        public String getCategory() { return category; }
        public String getStatement() { return statement; }
        public String getDetail() { return detail; }
        public String getAchievementStatus() { return achievementStatus; }
        public String getContributionType() { return contributionType; }
        public String getVerificationBasis() { return verificationBasis; }
        public String getMateriality() { return materiality; }
        public List<String> getAllowedEvidenceIds() { return allowedEvidenceIds; }
    }

    static final class EvidencePayload {
        private final String evidenceId;
        private final String title;
        private final String type;
        private final LocalDate periodStart;
        private final LocalDate periodEnd;
        private final int sourceCount;
        private final String summary;

        private EvidencePayload(AnswerPlanEvidence evidence) {
            this.evidenceId = evidence.getId();
            this.title = evidence.getTitle();
            this.type = evidence.getType();
            this.periodStart = evidence.getPeriodStart();
            this.periodEnd = evidence.getPeriodEnd();
            this.sourceCount = evidence.getSourceCount();
            this.summary = evidence.getSummary();
        }

        public String getEvidenceId() { return evidenceId; }
        public String getTitle() { return title; }
        public String getType() { return type; }
        public LocalDate getPeriodStart() { return periodStart; }
        public LocalDate getPeriodEnd() { return periodEnd; }
        public int getSourceCount() { return sourceCount; }
        public String getSummary() { return summary; }
    }

    static final class ExpressionPolicyPayload {
        private final String tone;
        private final int maxTitleLength;
        private final int maxSummaryLength;
        private final int maxSectionTitleLength;
        private final int maxSectionContentLength;
        private final boolean mustLabelSelfDeclared;
        private final boolean mustLabelInference;
        private final List<String> priorityCategories;

        private ExpressionPolicyPayload(AnswerPlan plan) {
            this.tone = plan.getExpressionPolicy().getTone().name();
            this.maxTitleLength = plan.getExpressionPolicy().getMaxTitleLength();
            this.maxSummaryLength = plan.getExpressionPolicy().getMaxSummaryLength();
            this.maxSectionTitleLength = plan.getExpressionPolicy().getMaxSectionTitleLength();
            this.maxSectionContentLength = plan.getExpressionPolicy().getMaxSectionContentLength();
            this.mustLabelSelfDeclared = plan.getExpressionPolicy().isMustLabelSelfDeclared();
            this.mustLabelInference = plan.getExpressionPolicy().isMustLabelInference();
            this.priorityCategories = plan.getExpressionPolicy().getPriorityCategories().stream()
                    .map(Enum::name)
                    .toList();
        }

        public String getTone() { return tone; }
        public int getMaxTitleLength() { return maxTitleLength; }
        public int getMaxSummaryLength() { return maxSummaryLength; }
        public int getMaxSectionTitleLength() { return maxSectionTitleLength; }
        public int getMaxSectionContentLength() { return maxSectionContentLength; }
        public boolean isMustLabelSelfDeclared() { return mustLabelSelfDeclared; }
        public boolean isMustLabelInference() { return mustLabelInference; }
        public List<String> getPriorityCategories() { return priorityCategories; }
    }
}
