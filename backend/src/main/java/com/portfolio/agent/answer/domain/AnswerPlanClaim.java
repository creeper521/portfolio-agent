package com.portfolio.agent.answer.domain;

import java.util.List;

public final class AnswerPlanClaim {

    private final String claimId;
    private final AnswerClaimCategory category;
    private final String statement;
    private final String detail;
    private final AnswerAchievementStatus achievementStatus;
    private final AnswerContributionType contributionType;
    private final AnswerVerificationBasis verificationBasis;
    private final AnswerClaimVerificationStatus verificationStatus;
    private final AnswerMateriality materiality;
    private final List<String> allowedEvidenceIds;

    public AnswerPlanClaim(
            String claimId,
            AnswerClaimCategory category,
            String statement,
            String detail,
            AnswerAchievementStatus achievementStatus,
            AnswerContributionType contributionType,
            AnswerVerificationBasis verificationBasis,
            AnswerClaimVerificationStatus verificationStatus,
            AnswerMateriality materiality,
            List<String> allowedEvidenceIds
    ) {
        this.claimId = claimId;
        this.category = category;
        this.statement = statement;
        this.detail = detail;
        this.achievementStatus = achievementStatus;
        this.contributionType = contributionType;
        this.verificationBasis = verificationBasis;
        this.verificationStatus = verificationStatus;
        this.materiality = materiality;
        this.allowedEvidenceIds = List.copyOf(allowedEvidenceIds);
    }

    public String getClaimId() { return claimId; }
    public AnswerClaimCategory getCategory() { return category; }
    public String getStatement() { return statement; }
    public String getDetail() { return detail; }
    public AnswerAchievementStatus getAchievementStatus() { return achievementStatus; }
    public AnswerContributionType getContributionType() { return contributionType; }
    public AnswerVerificationBasis getVerificationBasis() { return verificationBasis; }
    public AnswerClaimVerificationStatus getVerificationStatus() { return verificationStatus; }
    public AnswerMateriality getMateriality() { return materiality; }
    public List<String> getAllowedEvidenceIds() { return allowedEvidenceIds; }
}
