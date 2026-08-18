package com.portfolio.agent.turn.capability.portfolio.semantic;

import com.portfolio.agent.answer.domain.AnswerAchievementStatus;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerContributionType;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;
import com.portfolio.agent.turn.capability.portfolio.AuthorizedSubjectScope;
import com.portfolio.agent.turn.capability.portfolio.evidence.PublicSourceReference;
import com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceBundle;
import com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit;
import com.portfolio.agent.turn.planning.GoalSubjectReference;

import java.util.List;

final class PortfolioSemanticFixtures {
    private PortfolioSemanticFixtures() { }
    static ValidatedEvidenceUnit unit(
            String subjectId, String claimId, AnswerClaimCategory category) {
        AnswerClaimProjection claim = new AnswerClaimProjection(
                claimId, category, "statement " + claimId, "detail " + claimId,
                AnswerAchievementStatus.IMPLEMENTED_TESTED, AnswerContributionType.PRIMARY,
                AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.VERIFIED, AnswerMateriality.KEY,
                List.of("evidence-" + claimId));
        return new ValidatedEvidenceUnit(subjectId, claim,
                new PublicSourceReference(
                        "E-" + claimId, "Evidence", "public-1", "DOCUMENT",
                        "/projects/" + subjectId, "/evidence/" + claimId));
    }
    static ValidatedEvidenceBundle bundle(List<ValidatedEvidenceUnit> units, String... subjects) {
        AuthorizedSubjectScope scope = AuthorizedSubjectScope.exact(
                java.util.Arrays.stream(subjects).map(subject -> new GoalSubjectReference(
                        GoalSubjectReference.Kind.PROJECT, subject,
                        GoalSubjectReference.Basis.CONTINUATION, null)).toList(), "public-1");
        return new ValidatedEvidenceBundle(scope, "public-1", units);
    }
}
