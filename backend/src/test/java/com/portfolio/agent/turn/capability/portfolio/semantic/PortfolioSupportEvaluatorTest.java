package com.portfolio.agent.turn.capability.portfolio.semantic;

import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory;
import com.portfolio.agent.turn.capability.portfolio.retrieval.CorpusBackend;
import com.portfolio.agent.turn.capability.portfolio.retrieval.SearchStrategy;
import com.portfolio.agent.turn.capability.portfolio.AuthorizedSubjectScope;
import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceInvocation;
import com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceBundle;
import com.portfolio.agent.turn.planning.SemanticTask;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioSupportEvaluatorTest {
    @Test
    void factSelectsSupportedFacetsAndRecordsOnlyRealOmissions() {
        ValidatedEvidenceBundle bundle = PortfolioSemanticFixtures.bundle(List.of(
                PortfolioSemanticFixtures.unit(
                        "project-a", "background", AnswerClaimCategory.BACKGROUND)), "project-a");
        PortfolioSupportEvaluator.Evaluation evaluation = new PortfolioSupportEvaluator().fact(
                invocation(List.of(
                        PortfolioEvidenceInvocation.FacetProfile.BACKGROUND,
                        PortfolioEvidenceInvocation.FacetProfile.VERIFICATION)), bundle);
        assertThat(evaluation.hasSupport()).isTrue();
        assertThat(evaluation.coverage()).isEqualTo(PortfolioSemanticResult.Coverage.PARTIAL);
        assertThat(evaluation.getSelectedUnits()).hasSize(1);
        assertThat(evaluation.getOmissions()).containsExactly("VERIFICATION");
    }

    private PortfolioEvidenceInvocation invocation(
            List<PortfolioEvidenceInvocation.FacetProfile> facets) {
        return new PortfolioEvidenceInvocation(
                SemanticTask.Type.PORTFOLIO_FACT,
                AuthorizedSubjectScope.allPublished("public-1"), facets, List.of(),
                "public-1", CorpusBackend.BUNDLE, SearchStrategy.EXACT, null, null);
    }
}
