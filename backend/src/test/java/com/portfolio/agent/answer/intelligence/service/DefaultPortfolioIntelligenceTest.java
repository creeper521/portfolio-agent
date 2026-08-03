package com.portfolio.agent.answer.intelligence.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioIntelligenceResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRefinement;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedPassage;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedEvidenceReference;
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
    void constrainsAContextualFactLookupToTheValidatedStableSubjectId() {
        RecordingRetriever retriever = new RecordingRetriever(retrieval());
        DefaultPortfolioIntelligence intelligence = intelligence(retriever);
        PortfolioTask task = new PortfolioTask(
                "turn-1",
                "How was this verified?",
                PortfolioTaskMode.FACT_LOOKUP,
                1.0d,
                PortfolioConditions.empty(),
                null,
                null,
                "project-a");

        intelligence.resolve(task);

        assertThat(retriever.requests).singleElement().satisfies(request -> {
            assertThat(request.isExactPortfolioLookup()).isTrue();
            assertThat(request.getRequiredPortfolioIds()).containsExactly("project-a");
            assertThat(request.getQuery()).isEqualTo("How was this verified?");
        });
    }

    @Test
    void activeSubjectDoesNotNarrowRecommendationCandidates() {
        RecordingRetriever retriever = new RecordingRetriever(retrieval());
        DefaultPortfolioIntelligence intelligence = intelligence(retriever);
        PortfolioTask task = new PortfolioTask(
                "turn-1", "Recommend two projects", PortfolioTaskMode.RECOMMENDATION,
                1.0d,
                new PortfolioConditions("BACKEND", "INTERVIEWER", Set.of("JAVA"), null, 2),
                null,
                null,
                "project-a");

        intelligence.resolve(task);

        assertThat(retriever.requests).singleElement().satisfies(request -> {
            assertThat(request.isExactPortfolioLookup()).isFalse();
            assertThat(request.getRequiredPortfolioIds()).isEmpty();
        });
    }

    @Test
    void activeSubjectDoesNotTurnComparisonIntoSingleSubjectLookup() {
        RecordingRetriever retriever = new RecordingRetriever(retrieval());
        DefaultPortfolioIntelligence intelligence = intelligence(retriever);
        PortfolioTask task = new PortfolioTask(
                "turn-1", "Compare the projects", PortfolioTaskMode.COMPARISON,
                1.0d, PortfolioConditions.empty(), null, null, "project-a");

        intelligence.resolve(task);

        assertThat(retriever.requests).singleElement().satisfies(request -> {
            assertThat(request.isExactPortfolioLookup()).isFalse();
            assertThat(request.getRequiredPortfolioIds()).isEmpty();
        });
    }

    @Test
    void activeSubjectDoesNotNarrowRefinementRecomputation() {
        RecordingRetriever retriever = new RecordingRetriever(retrieval());
        DefaultPortfolioIntelligence intelligence = intelligence(retriever);
        PortfolioTask task = new PortfolioTask(
                "turn-1",
                "Replace one recommendation",
                PortfolioTaskMode.REFINE_RECOMMENDATION,
                1.0d,
                PortfolioConditions.empty(),
                TestRecommendationContexts.context(),
                new PortfolioRefinement(PortfolioConditions.empty(), Set.of("project-a")),
                "project-a");

        intelligence.resolve(task);

        assertThat(retriever.requests).hasSize(2);
        assertThat(retriever.requests.get(0).isExactPortfolioLookup()).isTrue();
        assertThat(retriever.requests.get(1).isExactPortfolioLookup()).isFalse();
        assertThat(retriever.requests.get(1).getRequiredPortfolioIds()).isEmpty();
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
    void doesNotPromotePendingRetrievedEvidenceToApproved() {
        PortfolioRetrievedSubject subject = subject("project-a", "Project A");
        PortfolioRetrievedPassage pendingPassage = new PortfolioRetrievedPassage(
                "project-a#claim-a", "project-a", "claim-a", "Pending claim",
                List.of(new PortfolioRetrievedEvidenceReference(
                        "evidence-a", "Pending evidence", "PENDING")));
        RecordingRetriever retriever = new RecordingRetriever(new PortfolioRetrievalResult(
                "public-1", List.of(subject), List.of(pendingPassage),
                new PortfolioRetrievalSource("TEST"), false, null));

        PortfolioIntelligenceResult result = intelligence(retriever).resolve(task(
                PortfolioTaskMode.RECOMMENDATION,
                new PortfolioConditions("BACKEND", "INTERVIEWER", Set.of("JAVA"), null, 2),
                null,
                null));

        assertThat(result.getPortfolioRecommendation().getItems()).isEmpty();
    }

    @Test
    void preservesRetrieverScoresWhenBuildingSelectionCandidates() {
        List<PortfolioRetrievedSubject> subjects = List.of(
                subject("project-a", "Project A", 0.1d),
                subject("project-b", "Project B", 0.9d),
                subject("project-c", "Project C", 0.8d));
        RecordingRetriever retriever = new RecordingRetriever(new PortfolioRetrievalResult(
                "public-1",
                subjects,
                List.of(
                        passage("project-a", "claim-a", "evidence-a"),
                        passage("project-b", "claim-b", "evidence-b"),
                        passage("project-c", "claim-c", "evidence-c")),
                new PortfolioRetrievalSource("TEST"),
                false,
                null));

        PortfolioIntelligenceResult result = intelligence(retriever).resolve(task(
                PortfolioTaskMode.RECOMMENDATION,
                new PortfolioConditions("BACKEND", "INTERVIEWER", Set.of("JAVA"), null, 2),
                null,
                null));

        assertThat(result.getPortfolioRecommendation().getItems())
                .extracting(item -> item.getPortfolioId())
                .containsExactly("project-b", "project-c");
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
        assertThat(retriever.requests.get(0).getRequiredPortfolioIds())
                .containsExactly("project-a", "project-b");
        assertThat(retriever.requests.get(0).getQuery())
                .isEqualTo("portfolio-context-validation")
                .doesNotContain("question");
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

    @Test
    void safelyRecomputesAfterAValidEmptyRecommendationContext() {
        RecordingRetriever retriever = new RecordingRetriever(retrieval());
        PortfolioRefinement refinement = new PortfolioRefinement(
                PortfolioConditions.empty(), Set.of());

        PortfolioIntelligenceResult result = intelligence(retriever).resolve(task(
                PortfolioTaskMode.REFINE_RECOMMENDATION,
                PortfolioConditions.empty(),
                TestRecommendationContexts.context(List.of(), 2),
                refinement));

        assertThat(retriever.requests).hasSize(2);
        assertThat(retriever.requests.get(0).isExactPortfolioLookup()).isTrue();
        assertThat(retriever.requests.get(0).getRequiredPortfolioIds()).isEmpty();
        assertThat(result.getResolvedIntent()).isEqualTo(PortfolioTaskMode.REFINE_RECOMMENDATION);
        assertThat(result.getPortfolioRecommendation()).isNotNull();
    }

    @Test
    void turnsDuplicateContextIdsIntoAClarificationBeforeRetrieval() {
        RecordingRetriever retriever = new RecordingRetriever(retrieval());

        PortfolioIntelligenceResult result = intelligence(retriever).resolve(task(
                PortfolioTaskMode.REFINE_RECOMMENDATION,
                PortfolioConditions.empty(),
                TestRecommendationContexts.context(List.of("project-a", "project-a"), 2),
                new PortfolioRefinement(PortfolioConditions.empty(), Set.of())));

        assertThat(retriever.requests).isEmpty();
        assertThat(result.getResolvedIntent()).isEqualTo(PortfolioTaskMode.CLARIFICATION_REQUIRED);
        assertThat(result.getClarification().getMissingCondition())
                .isEqualTo("recommendationContext");
    }

    @Test
    void turnsOversizedContextIdsIntoAClarificationBeforeRetrieval() {
        RecordingRetriever retriever = new RecordingRetriever(retrieval());

        PortfolioIntelligenceResult result = intelligence(retriever).resolve(task(
                PortfolioTaskMode.REFINE_RECOMMENDATION,
                PortfolioConditions.empty(),
                TestRecommendationContexts.context(
                        List.of("a", "b", "c", "d", "e", "f"), 5),
                new PortfolioRefinement(PortfolioConditions.empty(), Set.of())));

        assertThat(retriever.requests).isEmpty();
        assertThat(result.getResolvedIntent()).isEqualTo(PortfolioTaskMode.CLARIFICATION_REQUIRED);
        assertThat(result.getClarification().getMissingCondition())
                .isEqualTo("recommendationContext");
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
        return subject(id, title, 0.75d);
    }

    private PortfolioRetrievedSubject subject(String id, String title, double targetFit) {
        return new PortfolioRetrievedSubject(
                id, "PROJECT", title, "Summary " + id, "/projects/" + id,
                "BACKEND", Set.of("JAVA"), targetFit, 0.9d, 0.1d);
    }

    private PortfolioRetrievedPassage passage(String subjectId, String claimId, String evidenceId) {
        return new PortfolioRetrievedPassage(
                subjectId + "#" + claimId, subjectId, claimId, "Verified " + claimId,
                List.of(new PortfolioRetrievedEvidenceReference(
                        evidenceId, "Approved " + evidenceId, "APPROVED")));
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
