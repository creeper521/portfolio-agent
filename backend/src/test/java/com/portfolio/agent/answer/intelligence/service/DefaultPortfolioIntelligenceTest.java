package com.portfolio.agent.answer.intelligence.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioIntelligenceResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRefinement;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedPassage;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedSubject;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalRequest;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalSource;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTask;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetriever;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DefaultPortfolioIntelligenceTest {

    @Test
    void routesFactLookupOnlyThroughTheUnifiedRetriever() {
        RecordingRetriever retriever = new RecordingRetriever(retrieval());
        DefaultPortfolioIntelligence intelligence = intelligence(retriever);

        PortfolioIntelligenceResult result = intelligence.resolve(task(
                PortfolioTaskMode.FACT_LOOKUP, PortfolioConditions.empty(), null, null));

        assertThat(retriever.requests).singleElement().satisfies(request ->
                assertThat(request.getMode()).isEqualTo(PortfolioTaskMode.FACT_LOOKUP));
        assertThat(result.getResolvedIntent()).isEqualTo(PortfolioTaskMode.FACT_LOOKUP);
        assertThat(result.getEvidence()).extracting(PortfolioRetrievedPassage::getClaimId)
                .containsExactly("claim-a", "claim-b", "claim-c");
        assertThat(result.getPortfolioRecommendation()).isNull();
    }

    @Test
    void returnsOneClarificationWithoutCallingRetrieverWhenRecommendationAudienceIsMissing() {
        RecordingRetriever retriever = new RecordingRetriever(retrieval());
        DefaultPortfolioIntelligence intelligence = intelligence(retriever);

        PortfolioIntelligenceResult result = intelligence.resolve(task(
                PortfolioTaskMode.RECOMMENDATION,
                new PortfolioConditions("BACKEND", null, Set.of("JAVA"), null, 2), null, null));

        assertThat(retriever.requests).isEmpty();
        assertThat(result.getResolvedIntent()).isEqualTo(PortfolioTaskMode.CLARIFICATION_REQUIRED);
        assertThat(result.getClarification().getMissingCondition()).isEqualTo("audienceRole");
    }

    @Test
    void createsRecommendationOnlyFromRetrievedApprovedClaimEvidence() {
        RecordingRetriever retriever = new RecordingRetriever(retrieval());
        DefaultPortfolioIntelligence intelligence = intelligence(retriever);

        PortfolioIntelligenceResult result = intelligence.resolve(task(
                PortfolioTaskMode.RECOMMENDATION,
                new PortfolioConditions("BACKEND", "INTERVIEWER", Set.of("JAVA"), null, 2), null, null));

        assertThat(result.getPortfolioRecommendation().getItems())
                .extracting(item -> item.getPortfolioId())
                .containsExactly("project-a", "project-b");
        assertThat(result.getPortfolioRecommendation().getItems().get(0).getEvidenceIds())
                .containsExactly("evidence-a");
        assertThat(result.getPortfolioRecommendation().getContext().getSelectedPortfolioIds())
                .containsExactly("project-a", "project-b");
    }

    @Test
    void validatesReturnedContextBeforeApplyingRefinementAndRecomputingRecommendation() {
        RecordingRetriever retriever = new RecordingRetriever(retrieval());
        DefaultPortfolioIntelligence intelligence = intelligence(retriever);
        PortfolioRefinement refinement = new PortfolioRefinement(
                PortfolioConditions.empty(), Set.of("project-a"));

        PortfolioIntelligenceResult result = intelligence.resolve(task(
                PortfolioTaskMode.REFINE_RECOMMENDATION,
                PortfolioConditions.empty(), TestRecommendationContexts.context(), refinement));

        assertThat(retriever.requests).hasSize(2);
        assertThat(result.getPortfolioRecommendation().getItems())
                .extracting(item -> item.getPortfolioId())
                .containsExactly("project-b", "project-c");
    }

    @Test
    void turnsAContextMismatchIntoOneClarificationWithoutApplyingRefinement() {
        RecordingRetriever retriever = new RecordingRetriever(retrieval());
        DefaultPortfolioIntelligence intelligence = intelligence(retriever);
        PortfolioRefinement refinement = new PortfolioRefinement(
                PortfolioConditions.empty(), Set.of("project-a"));

        PortfolioIntelligenceResult result = intelligence.resolve(task(
                PortfolioTaskMode.REFINE_RECOMMENDATION,
                PortfolioConditions.empty(), contextWithMismatchedVersion(), refinement));

        assertThat(retriever.requests).singleElement();
        assertThat(result.getResolvedIntent()).isEqualTo(PortfolioTaskMode.CLARIFICATION_REQUIRED);
        assertThat(result.getClarification().getMissingCondition()).isEqualTo("recommendationContext");
    }

    private DefaultPortfolioIntelligence intelligence(PortfolioRetriever retriever) {
        return new DefaultPortfolioIntelligence(
                new PortfolioTaskValidator(), retriever, new PortfolioRecommendationPolicy(),
                new RecommendationContextValidator(new RecommendationBatchFingerprint()));
    }

    private PortfolioTask task(
            PortfolioTaskMode mode,
            PortfolioConditions conditions,
            com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationContext context,
            PortfolioRefinement refinement) {
        return new PortfolioTask("turn-1", "question", mode, 1.0d, conditions, context, refinement);
    }

    private com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationContext contextWithMismatchedVersion() {
        com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationContext valid =
                TestRecommendationContexts.context();
        return new com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationContext(
                valid.getRecommendationBatchId(), "public-old", valid.getCareerTrack(),
                valid.getAudienceRole(), valid.getCapabilityCodes(), valid.getRequestedSize(),
                valid.getSelectedPortfolioIds());
    }

    private PortfolioRetrievalResult retrieval() {
        List<PortfolioRetrievedSubject> subjects = List.of(
                subject("project-a", "Project A"), subject("project-b", "Project B"),
                subject("project-c", "Project C"));
        List<PortfolioRetrievedPassage> passages = List.of(
                passage("project-a", "claim-a", "evidence-a"),
                passage("project-b", "claim-b", "evidence-b"),
                passage("project-c", "claim-c", "evidence-c"));
        return new PortfolioRetrievalResult(
                "public-1", subjects, passages, new PortfolioRetrievalSource("TEST"), false, null);
    }

    private PortfolioRetrievedSubject subject(String id, String title) {
        return new PortfolioRetrievedSubject(
                id, "PROJECT", title, "Summary " + id, "/projects/" + id,
                "BACKEND", Set.of("JAVA"));
    }

    private PortfolioRetrievedPassage passage(String subjectId, String claimId, String evidenceId) {
        return new PortfolioRetrievedPassage(
                subjectId + "#" + claimId, subjectId, claimId, "Verified " + claimId,
                List.of(evidenceId));
    }

    private static final class RecordingRetriever implements PortfolioRetriever {

        private final PortfolioRetrievalResult result;
        private final List<PortfolioRetrievalRequest> requests = new ArrayList<>();

        private RecordingRetriever(PortfolioRetrievalResult result) {
            this.result = result;
        }

        @Override
        public PortfolioRetrievalResult retrieve(PortfolioRetrievalRequest request) {
            requests.add(request);
            return result;
        }
    }
}
