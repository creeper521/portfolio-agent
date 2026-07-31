package com.portfolio.agent.answer.intelligence.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendation;
import com.portfolio.agent.selection.domain.EvidenceReference;
import com.portfolio.agent.selection.domain.PortfolioSubjectKind;
import com.portfolio.agent.selection.domain.SelectionCandidate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PortfolioRecommendationPolicyTest {

    private final PortfolioRecommendationPolicy policy = new PortfolioRecommendationPolicy();

    @Test
    void producesTheSameOrderedRecommendationForEquivalentInputsAndExclusions() {
        PortfolioConditions conditions = new PortfolioConditions(
                "java_backend", "interviewer", Set.of("RAG", "JAVA"), "ignored", null);
        List<SelectionCandidate> candidates = List.of(
                candidate("PROJECT-B", Set.of("JAVA"), 0.8),
                candidate("PROJECT-A", Set.of("JAVA", "RAG"), 0.9),
                candidate("PROJECT-C", Set.of("JAVA", "RAG"), 0.7));

        PortfolioRecommendation first = policy.recommend(
                "public-2026-07-31", conditions, candidates, Set.of("PROJECT-B"));
        PortfolioRecommendation second = policy.recommend(
                "public-2026-07-31", conditions, candidates, Set.of("PROJECT-B"));

        assertThat(first.getItems()).extracting(item -> item.getPortfolioId())
                .containsExactly("PROJECT-A", "PROJECT-C");
        assertThat(second).isEqualTo(first);
        assertThat(first.getContext().getRequestedSize()).isEqualTo(3);
        assertThat(first.getItems().get(0).getMatchReasons())
                .containsExactly("careerTrack:JAVA_BACKEND", "capability:JAVA", "capability:RAG");
    }

    @Test
    void appliesAnExplicitRequestedSizeAndExcludesTheReplacementTarget() {
        PortfolioConditions conditions = new PortfolioConditions(
                "JAVA_BACKEND", "INTERVIEWER", Set.of("JAVA"), null, 2);

        PortfolioRecommendation recommendation = policy.recommend(
                "public-2026-07-31",
                conditions,
                List.of(
                        candidate("PROJECT-A", Set.of("JAVA"), 0.9),
                        candidate("PROJECT-B", Set.of("JAVA"), 0.8),
                        candidate("PROJECT-C", Set.of("JAVA"), 0.7)),
                Set.of("PROJECT-A"));

        assertThat(recommendation.getItems()).extracting(item -> item.getPortfolioId())
                .containsExactly("PROJECT-B", "PROJECT-C");
        assertThat(recommendation.getContext().getRequestedSize()).isEqualTo(2);
    }

    @Test
    void excludesCandidatesWithoutAtLeastOneApprovedEvidenceBeforeSelection() {
        PortfolioConditions conditions = new PortfolioConditions(
                "JAVA_BACKEND", "INTERVIEWER", Set.of("JAVA"), null, 2);

        PortfolioRecommendation recommendation = policy.recommend(
                "public-2026-07-31",
                conditions,
                List.of(
                        candidateWithoutEvidence("PROJECT-A", Set.of("JAVA"), 0.99),
                        candidate("PROJECT-B", Set.of("JAVA"), 0.8),
                        candidate("PROJECT-C", Set.of("JAVA"), 0.7)),
                Set.of());

        assertThat(recommendation.getItems()).extracting(item -> item.getPortfolioId())
                .containsExactly("PROJECT-B", "PROJECT-C");
    }

    private SelectionCandidate candidate(String id, Set<String> capabilityCodes, double fit) {
        return new SelectionCandidate(
                id,
                PortfolioSubjectKind.PROJECT,
                "Title " + id,
                "Summary " + id,
                "/projects/" + id.toLowerCase(),
                "JAVA_BACKEND",
                capabilityCodes,
                List.of(new EvidenceReference("claim-" + id, "evidence-" + id, "Evidence")),
                fit,
                0.9,
                0.0);
    }

    private SelectionCandidate candidateWithoutEvidence(String id, Set<String> capabilityCodes, double fit) {
        return new SelectionCandidate(
                id,
                PortfolioSubjectKind.PROJECT,
                "Title " + id,
                "Summary " + id,
                "/projects/" + id.toLowerCase(),
                "JAVA_BACKEND",
                capabilityCodes,
                List.of(),
                fit,
                0.9,
                0.0);
    }
}
