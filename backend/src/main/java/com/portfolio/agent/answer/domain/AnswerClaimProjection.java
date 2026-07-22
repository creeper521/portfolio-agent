package com.portfolio.agent.answer.domain;

import java.util.List;
import java.util.Objects;

public final class AnswerClaimProjection {
    private final String id;
    private final AnswerClaimCategory category;
    private final String statement;
    private final String detail;
    private final AnswerAchievementStatus achievementStatus;
    private final AnswerContributionType contributionType;
    private final AnswerVerificationBasis verificationBasis;
    private final AnswerClaimVerificationStatus verificationStatus;
    private final AnswerMateriality materiality;
    private final List<String> topics;
    private final List<String> directEvidenceIds;

    public AnswerClaimProjection(
            String id,
            AnswerClaimCategory category,
            String statement,
            String detail,
            AnswerAchievementStatus achievementStatus,
            AnswerContributionType contributionType,
            AnswerVerificationBasis verificationBasis,
            AnswerClaimVerificationStatus verificationStatus,
            AnswerMateriality materiality,
            List<String> directEvidenceIds) {
        this(id, category, statement, detail, achievementStatus, contributionType,
                verificationBasis, verificationStatus, materiality, List.of(), directEvidenceIds);
    }

    public AnswerClaimProjection(
            String id,
            AnswerClaimCategory category,
            String statement,
            String detail,
            AnswerAchievementStatus achievementStatus,
            AnswerContributionType contributionType,
            AnswerVerificationBasis verificationBasis,
            AnswerClaimVerificationStatus verificationStatus,
            AnswerMateriality materiality,
            List<String> topics,
            List<String> directEvidenceIds) {
        this.id = id;
        this.category = category;
        this.statement = statement;
        this.detail = detail;
        this.achievementStatus = achievementStatus;
        this.contributionType = contributionType;
        this.verificationBasis = verificationBasis;
        this.verificationStatus = verificationStatus;
        this.materiality = materiality;
        this.topics = List.copyOf(topics);
        this.directEvidenceIds = List.copyOf(directEvidenceIds);
    }

    public AnswerClaimProjection(String id, AnswerClaimCategory category,
            AnswerVerificationBasis verificationBasis,
            AnswerClaimVerificationStatus verificationStatus,
            AnswerMateriality materiality, List<String> directEvidenceIds) {
        this(id, category, "", "", AnswerAchievementStatus.UNKNOWN,
                AnswerContributionType.PRIMARY, verificationBasis, verificationStatus,
                materiality, directEvidenceIds);
    }

    public String getId() { return id; }
    public AnswerClaimCategory getCategory() { return category; }
    public String getStatement() { return statement; }
    public String getDetail() { return detail; }
    public AnswerAchievementStatus getAchievementStatus() { return achievementStatus; }
    public AnswerContributionType getContributionType() { return contributionType; }
    public AnswerVerificationBasis getVerificationBasis() { return verificationBasis; }
    public AnswerClaimVerificationStatus getVerificationStatus() { return verificationStatus; }
    public AnswerMateriality getMateriality() { return materiality; }
    public List<String> getTopics() { return topics; }
    public List<String> getDirectEvidenceIds() { return directEvidenceIds; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AnswerClaimProjection that)) return false;
        return Objects.equals(id, that.id) && category == that.category
                && Objects.equals(statement, that.statement)
                && Objects.equals(detail, that.detail)
                && achievementStatus == that.achievementStatus
                && contributionType == that.contributionType
                && verificationBasis == that.verificationBasis
                && verificationStatus == that.verificationStatus
                && materiality == that.materiality
                && Objects.equals(topics, that.topics)
                && Objects.equals(directEvidenceIds, that.directEvidenceIds);
    }
    @Override public int hashCode() {
        return Objects.hash(id, category, statement, detail, achievementStatus,
                contributionType, verificationBasis, verificationStatus,
                materiality, topics, directEvidenceIds);
    }
}
