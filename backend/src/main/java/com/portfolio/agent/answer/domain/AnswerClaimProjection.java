package com.portfolio.agent.answer.domain;

import java.util.List;
import java.util.Objects;

public final class AnswerClaimProjection {
    private final String id;
    private final AnswerClaimCategory category;
    private final AnswerVerificationBasis verificationBasis;
    private final AnswerClaimVerificationStatus verificationStatus;
    private final AnswerMateriality materiality;
    private final List<String> directEvidenceIds;

    public AnswerClaimProjection(String id, AnswerClaimCategory category,
            AnswerVerificationBasis verificationBasis,
            AnswerClaimVerificationStatus verificationStatus,
            AnswerMateriality materiality, List<String> directEvidenceIds) {
        this.id = id;
        this.category = category;
        this.verificationBasis = verificationBasis;
        this.verificationStatus = verificationStatus;
        this.materiality = materiality;
        this.directEvidenceIds = List.copyOf(directEvidenceIds);
    }

    public String getId() { return id; }
    public AnswerClaimCategory getCategory() { return category; }
    public AnswerVerificationBasis getVerificationBasis() { return verificationBasis; }
    public AnswerClaimVerificationStatus getVerificationStatus() { return verificationStatus; }
    public AnswerMateriality getMateriality() { return materiality; }
    public List<String> getDirectEvidenceIds() { return directEvidenceIds; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AnswerClaimProjection that)) return false;
        return Objects.equals(id, that.id) && category == that.category
                && verificationBasis == that.verificationBasis
                && verificationStatus == that.verificationStatus
                && materiality == that.materiality
                && Objects.equals(directEvidenceIds, that.directEvidenceIds);
    }
    @Override public int hashCode() {
        return Objects.hash(id, category, verificationBasis, verificationStatus,
                materiality, directEvidenceIds);
    }
}
