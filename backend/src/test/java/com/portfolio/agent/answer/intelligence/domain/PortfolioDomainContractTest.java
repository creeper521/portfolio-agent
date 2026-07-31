package com.portfolio.agent.answer.intelligence.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PortfolioDomainContractTest {

    @Test
    void mergesConditionsWithoutMutatingEitherInput() {
        Set<String> capabilityCodes = new LinkedHashSet<>(List.of("POSTGRESQL"));
        PortfolioConditions base = new PortfolioConditions(
                "BACKEND", "INTERVIEWER", capabilityCodes, "prepare interview", 2);
        PortfolioConditions refinement = new PortfolioConditions(
                null, "MENTOR", Set.of("RAG"), null, 2);

        capabilityCodes.add("MUTATED");
        PortfolioConditions merged = base.merge(refinement);

        assertThat(base.getCapabilityCodes()).containsExactly("POSTGRESQL");
        assertThat(merged.getCareerTrack()).isEqualTo("BACKEND");
        assertThat(merged.getAudienceRole()).isEqualTo("MENTOR");
        assertThat(merged.getCapabilityCodes()).containsExactlyInAnyOrder("POSTGRESQL", "RAG");
        assertThat(merged.getGoal()).isEqualTo("prepare interview");
        assertThat(merged.getRequestedSize()).isEqualTo(2);
        assertThat(PortfolioConditions.empty().merge(base)).isEqualTo(base);
        assertThat(base.merge(PortfolioConditions.empty())).isEqualTo(base);
    }

    @Test
    void suppliesDefaultRequestedSizeWithoutMarkingItExplicitlySpecified() {
        PortfolioConditions empty = PortfolioConditions.empty();
        PortfolioConditions explicitDefault = new PortfolioConditions(
                null, null, Set.of(), null, 3);

        assertThat(empty.getRequestedSize()).isEqualTo(3);
        assertThat(empty.hasRequestedSize()).isFalse();
        assertThat(explicitDefault.hasRequestedSize()).isTrue();
        assertThat(empty).isNotEqualTo(explicitDefault);
    }

    @Test
    void rejectsInvalidRecommendationContextInputs() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PortfolioRecommendationContext(
                "invalid", "public-2026-07-31", "BACKEND", "INTERVIEWER",
                Set.of("RAG"), 2, List.of("project-1")));
        assertThatIllegalArgumentException().isThrownBy(() -> new PortfolioRecommendationContext(
                batchId(), "", "BACKEND", "INTERVIEWER", Set.of("RAG"), 2,
                List.of("project-1")));
        assertThatIllegalArgumentException().isThrownBy(() -> new PortfolioRecommendationContext(
                batchId(), "public-2026-07-31", "BACKEND", "INTERVIEWER",
                Set.of("RAG"), 6, List.of("project-1")));
    }

    @Test
    void defensivelyCopiesRecommendationCollectionsWhilePreservingPortfolioOrder() {
        Set<String> capabilityCodes = new LinkedHashSet<>(List.of("RAG"));
        List<String> portfolioIds = new ArrayList<>(List.of("project-1", "case-2"));

        PortfolioRecommendationContext context = new PortfolioRecommendationContext(
                batchId(), "public-2026-07-31", "BACKEND", "INTERVIEWER",
                capabilityCodes, 2, portfolioIds);

        capabilityCodes.add("MUTATED");
        portfolioIds.add("project-3");

        assertThat(context.getCapabilityCodes()).containsExactly("RAG");
        assertThat(context.getSelectedPortfolioIds()).containsExactly("project-1", "case-2");
    }

    @Test
    void rejectsOutOfRangeRequestedSizeForConditionsAndTasks() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PortfolioConditions(
                null, null, Set.of(), null, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> new PortfolioTask(
                "turn-1", "recommend something", PortfolioTaskMode.RECOMMENDATION,
                1.1d, PortfolioConditions.empty(), null, null));
    }

    @Test
    void rejectsNonFiniteTaskConfidence() {
        assertThatIllegalArgumentException().isThrownBy(() -> task(Double.NaN));
        assertThatIllegalArgumentException().isThrownBy(() -> task(Double.POSITIVE_INFINITY));
        assertThatIllegalArgumentException().isThrownBy(() -> task(Double.NEGATIVE_INFINITY));
    }

    @Test
    void recommendationValueObjectsDoNotExposeMutableCollections() {
        List<String> reasons = new ArrayList<>(List.of("matches backend skills"));
        List<String> evidenceIds = new ArrayList<>(List.of("evidence-1"));
        PortfolioRecommendationItem item = new PortfolioRecommendationItem(
                "project-1", "Project one", "/projects/project-1", reasons, evidenceIds);
        List<PortfolioRecommendationItem> items = new ArrayList<>(List.of(item));
        PortfolioRecommendation recommendation = new PortfolioRecommendation(
                batchId(), context(), items, List.of("audienceRole"), List.of());

        reasons.add("mutated");
        evidenceIds.add("mutated");
        items.clear();

        assertThat(recommendation.getItems()).containsExactly(item);
        assertThat(recommendation.getItems().getFirst().getMatchReasons())
                .containsExactly("matches backend skills");
        assertThat(recommendation.getItems().getFirst().getEvidenceIds())
                .containsExactly("evidence-1");
    }

    @Test
    void keepsRetrievalWindowSeparateFromRecommendationSize() {
        PortfolioRetrievalRequest defaultRequest = new PortfolioRetrievalRequest(
                "find PostgreSQL work", PortfolioTaskMode.FACT_LOOKUP,
                PortfolioConditions.empty());
        PortfolioRetrievalRequest explicitRequest = new PortfolioRetrievalRequest(
                "compare projects", PortfolioTaskMode.COMPARISON,
                new PortfolioConditions(null, "INTERVIEWER", Set.of(), null, 2), 50);

        assertThat(defaultRequest.getLimit()).isEqualTo(20);
        assertThat(explicitRequest.getLimit()).isEqualTo(50);
        assertThat(explicitRequest.getConditions().getRequestedSize()).isEqualTo(2);
        assertThatIllegalArgumentException().isThrownBy(() -> new PortfolioRetrievalRequest(
                "too many", PortfolioTaskMode.FACT_LOOKUP, PortfolioConditions.empty(), 51));
    }

    @Test
    void exposesRetrievalFactsWithoutCouplingThemToRecommendationItems() {
        PortfolioRetrievedSubject subject = new PortfolioRetrievedSubject(
                "project-1", "PROJECT", "Project one", "Public summary", "/projects/project-1",
                Set.of("POSTGRESQL"));
        PortfolioRetrievedPassage passage = new PortfolioRetrievedPassage(
                "project-1#claim-1", "project-1", "claim-1", "Public verified fact",
                List.of("evidence-1"));
        PortfolioRetrievalResult result = new PortfolioRetrievalResult(
                "public-2026-07-31", List.of(subject), List.of(passage),
                new PortfolioRetrievalSource("BUNDLE"), false, null);

        assertThat(result.getSubjects()).containsExactly(subject);
        assertThat(result.getPassages()).containsExactly(passage);
        assertThat(result.getSource().getAdapterId()).isEqualTo("BUNDLE");
    }

    private PortfolioRecommendationContext context() {
        return new PortfolioRecommendationContext(
                batchId(), "public-2026-07-31", "BACKEND", "INTERVIEWER",
                Set.of("RAG"), 2, List.of("project-1", "case-2"));
    }

    private String batchId() {
        return "rec_0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    }

    private PortfolioTask task(double confidence) {
        return new PortfolioTask(
                "turn-1", "recommend something", PortfolioTaskMode.RECOMMENDATION,
                confidence, PortfolioConditions.empty(), null, null);
    }
}
