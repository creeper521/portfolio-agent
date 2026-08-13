package com.portfolio.agent.answer.composition.domain;

import com.portfolio.agent.answer.domain.PublicSourceReferenceValue;
import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import java.util.List;
import java.util.Objects;

/** Approved public claim projection. Presentation policy lives in ExpressionStatement. */
public final class GroundedStatement {
    private final StatementType statementType;
    private final List<SubjectReference> subjectReferences;
    private final ControlledPredicate controlledPredicate;
    private final String publicStatement;
    private final String publicDetail;
    private final AnswerClaimCategory claimCategory;
    private final AnswerAchievementStatus achievementStatus;
    private final AnswerContributionType contributionType;
    private final AnswerVerificationBasis verificationBasis;
    private final AnswerMateriality materiality;
    private final SupportTarget supportTarget;
    private final List<PublicSourceReferenceValue> publicSourceReferences;

    public GroundedStatement(
            StatementType statementType,
            List<SubjectReference> subjectReferences,
            ControlledPredicate controlledPredicate,
            String publicStatement,
            String publicDetail,
            AnswerClaimCategory claimCategory,
            AnswerAchievementStatus achievementStatus,
            AnswerContributionType contributionType,
            AnswerVerificationBasis verificationBasis,
            AnswerMateriality materiality,
            SupportTarget supportTarget,
            List<PublicSourceReferenceValue> publicSourceReferences) {
        this.statementType = Objects.requireNonNull(statementType, "statementType");
        this.subjectReferences = DomainValues.distinctCopy(subjectReferences, "subjectReferences");
        if (this.subjectReferences.isEmpty()) {
            throw new IllegalArgumentException("subjectReferences must not be empty");
        }
        this.controlledPredicate = Objects.requireNonNull(controlledPredicate, "controlledPredicate");
        this.publicStatement = DomainValues.requireText(publicStatement, "publicStatement");
        this.publicDetail = DomainValues.optionalText(publicDetail, "publicDetail");
        this.claimCategory = Objects.requireNonNull(claimCategory, "claimCategory");
        this.achievementStatus = Objects.requireNonNull(achievementStatus, "achievementStatus");
        this.contributionType = Objects.requireNonNull(contributionType, "contributionType");
        this.verificationBasis = Objects.requireNonNull(verificationBasis, "verificationBasis");
        this.materiality = Objects.requireNonNull(materiality, "materiality");
        this.supportTarget = Objects.requireNonNull(supportTarget, "supportTarget");
        this.publicSourceReferences = DomainValues.distinctCopy(
                publicSourceReferences, "publicSourceReferences");
        if (this.publicSourceReferences.isEmpty()) {
            throw new IllegalArgumentException("publicSourceReferences must not be empty");
        }
    }

    public StatementType getStatementType() { return statementType; }
    public List<SubjectReference> getSubjectReferences() { return subjectReferences; }
    public ControlledPredicate getControlledPredicate() { return controlledPredicate; }
    public String getPublicStatement() { return publicStatement; }
    public String getPublicDetail() { return publicDetail; }
    public AnswerClaimCategory getClaimCategory() { return claimCategory; }
    public AnswerAchievementStatus getAchievementStatus() { return achievementStatus; }
    public AnswerContributionType getContributionType() { return contributionType; }
    public AnswerVerificationBasis getVerificationBasis() { return verificationBasis; }
    public AnswerMateriality getMateriality() { return materiality; }
    public SupportTarget getSupportTarget() { return supportTarget; }
    public List<PublicSourceReferenceValue> getPublicSourceReferences() { return publicSourceReferences; }
    public List<PublicSourceReferenceValue> getSourceReferences() { return publicSourceReferences; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof GroundedStatement that)) return false;
        return statementType == that.statementType
                && subjectReferences.equals(that.subjectReferences)
                && controlledPredicate == that.controlledPredicate
                && publicStatement.equals(that.publicStatement)
                && Objects.equals(publicDetail, that.publicDetail)
                && claimCategory == that.claimCategory
                && achievementStatus == that.achievementStatus
                && contributionType == that.contributionType
                && verificationBasis == that.verificationBasis
                && materiality == that.materiality
                && supportTarget == that.supportTarget
                && publicSourceReferences.equals(that.publicSourceReferences);
    }

    @Override
    public int hashCode() {
        return Objects.hash(statementType, subjectReferences, controlledPredicate, publicStatement,
                publicDetail, claimCategory, achievementStatus, contributionType, verificationBasis,
                materiality, supportTarget, publicSourceReferences);
    }

    @Override
    public String toString() {
        return "GroundedStatement{statementType=" + statementType
                + ", subjectCount=" + subjectReferences.size()
                + ", sourceCount=" + publicSourceReferences.size() + '}';
    }
}
