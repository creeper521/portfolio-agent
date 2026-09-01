package com.portfolio.agent.turn.capability.portfolio.semantic;

import com.portfolio.agent.turn.capability.portfolio.AuthorizedSubjectScope;
import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceInvocation;
import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceCapability;
import com.portfolio.agent.turn.capability.portfolio.PortfolioSubjectKind;
import com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceBundle;
import com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceUnit;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerAchievementStatus;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimProjection;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimVerificationStatus;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerContributionType;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerMateriality;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerVerificationBasis;
import com.portfolio.agent.turn.capability.portfolio.retrieval.CorpusBackend;
import com.portfolio.agent.turn.capability.portfolio.retrieval.SearchStrategy;
import com.portfolio.agent.turn.execution.PublicSourceReferenceValue;
import com.portfolio.agent.turn.planning.GoalKind;
import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.SemanticTaskParameters;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioSemanticSubjectKindContractTest {
    private static final String RELEASE = "public-1";
    private final PortfolioSemanticResultFactory factory =
            new PortfolioSemanticResultFactory(new PortfolioSupportEvaluator());

    @Test
    void recommendationRejectsMixedValidatedSubjectKindsAsOneBatch() {
        PortfolioEvidenceInvocation invocation = invocation(2);
        ValidatedEvidenceBundle bundle = bundle(List.of(
                unit("project-a", PortfolioSubjectKind.PROJECT),
                unit("case-a", PortfolioSubjectKind.CASE)));

        assertThatThrownBy(() -> factory.create(task(2), invocation, bundle))
                .isInstanceOfSatisfying(
                        PortfolioEvidenceCapability.PortfolioCapabilityException.class,
                        failure -> assertThat(failure.getIntegrityReason())
                                .contains(PortfolioEvidenceCapability.IntegrityReason
                                        .RECOMMENDATION_SUBJECT_KIND_CONTRACT_VIOLATION));
    }

    @Test
    void recommendationKeepsNoResultAndPartialSemanticsInsideProjectContract() {
        PortfolioEvidenceInvocation invocation = invocation(2);

        assertThat(factory.create(task(2), invocation, bundle(List.of())))
                .isEmpty();
        PortfolioSemanticResult.Recommendation partial =
                (PortfolioSemanticResult.Recommendation) factory.create(
                        task(2), invocation,
                        bundle(List.of(unit(
                                "project-a", PortfolioSubjectKind.PROJECT))))
                        .orElseThrow();

        assertThat(partial.getCoverage())
                .isEqualTo(PortfolioSemanticResult.Coverage.PARTIAL);
        assertThat(partial.getSelectedSubjectIds())
                .containsExactly("project-a");
        assertThat(partial.getOmissions())
                .containsExactly("REQUESTED_SIZE");
    }

    private PortfolioEvidenceInvocation invocation(int requestedSize) {
        AuthorizedSubjectScope scope = AuthorizedSubjectScope.allPublished(RELEASE);
        return new PortfolioEvidenceInvocation(
                SemanticTask.Type.PORTFOLIO_RECOMMEND,
                scope,
                Set.of(PortfolioSubjectKind.PROJECT),
                List.of(PortfolioEvidenceInvocation.FacetProfile.RECOMMENDATION),
                List.of(),
                UserGoalProposal.Depth.STANDARD,
                requestedSize,
                Set.of(),
                RELEASE,
                CorpusBackend.BUNDLE,
                SearchStrategy.HYBRID,
                null,
                null);
    }

    private SemanticTask task(int requestedSize) {
        return SemanticTask.of(
                "task-recommendation",
                SemanticTask.Type.PORTFOLIO_RECOMMEND,
                new SemanticTaskParameters(
                        GoalKind.PORTFOLIO_RECOMMEND,
                        new UserGoalProposal.PortfolioRecommendationParameters(
                                requestedSize, Set.of()),
                        List.of()));
    }

    private ValidatedEvidenceBundle bundle(List<ValidatedEvidenceUnit> units) {
        return new ValidatedEvidenceBundle(
                AuthorizedSubjectScope.allPublished(RELEASE), RELEASE, units);
    }

    private ValidatedEvidenceUnit unit(
            String subjectId, PortfolioSubjectKind subjectKind) {
        String evidenceKey = "evidence-" + subjectId;
        AnswerClaimProjection claim = new AnswerClaimProjection(
                "claim-" + subjectId,
                AnswerClaimCategory.IMPLEMENTATION,
                "公开实现证据 " + subjectId,
                "公开实现证据",
                AnswerAchievementStatus.DELIVERED,
                AnswerContributionType.PRIMARY,
                AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                AnswerClaimVerificationStatus.VERIFIED,
                AnswerMateriality.KEY,
                List.of(evidenceKey));
        String routePrefix = subjectKind == PortfolioSubjectKind.PROJECT
                ? "/projects/" : "/cases/";
        return new ValidatedEvidenceUnit(
                subjectId,
                subjectKind,
                "公开主体 " + subjectId,
                null,
                Set.of(),
                claim,
                new PublicSourceReferenceValue(
                        evidenceKey,
                        "Evidence " + subjectId,
                        RELEASE,
                        "DOCUMENT",
                        routePrefix + subjectId,
                        "/evidence/" + evidenceKey));
    }
}
