package com.portfolio.agent.selection.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.agent.selection.domain.CandidateRetrievalResult;
import com.portfolio.agent.selection.domain.EvidenceReference;
import com.portfolio.agent.selection.domain.PortfolioSelectionResult;
import com.portfolio.agent.selection.domain.PortfolioSelectionStatus;
import com.portfolio.agent.selection.domain.PortfolioSubjectKind;
import com.portfolio.agent.selection.domain.RetrievalMode;
import com.portfolio.agent.selection.domain.SelectionCandidate;
import com.portfolio.agent.selection.domain.SelectionTarget;
import com.portfolio.agent.selection.dto.PortfolioSelectionResponse;
import com.portfolio.agent.selection.service.ExhaustiveSelectionStrategy;
import com.portfolio.agent.selection.service.PortfolioSelectionService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PortfolioSelectionResponseMapperTest {

    @Test
    void mapsDeterministicIdentityCoverageEvidenceComplementarityAndAlternatives() {
        SelectionCandidate project = candidate(
                "PROJECT-01", PortfolioSubjectKind.PROJECT, Set.of("JAVA", "DELIVERY"),
                List.of(
                        new EvidenceReference("CLAIM-01", "EVIDENCE-01", "公开验收记录"),
                        new EvidenceReference(
                                "CLAIM-PRIVATE",
                                "EVIDENCE-PRIVATE",
                                "不得公开",
                                "PENDING")),
                0.95);
        SelectionCandidate incident = candidate(
                "CASE-02", PortfolioSubjectKind.CASE, Set.of("INCIDENT_ANALYSIS"),
                List.of(new EvidenceReference("CLAIM-02", "EVIDENCE-02", "公开复盘")), 0.90);
        SelectionCandidate alternative = candidate(
                "CASE-03", PortfolioSubjectKind.CASE, Set.of("JAVA"), List.of(), 0.40);
        SelectionTarget target = new SelectionTarget(
                "JAVA_BACKEND",
                "INTERVIEWER",
                Set.of("JAVA", "INCIDENT_ANALYSIS"),
                "展示工程交付与故障分析",
                2);
        PortfolioSelectionService service = new PortfolioSelectionService(
                (ignored, limit) -> new CandidateRetrievalResult(
                        "2026-07-30.1",
                        RetrievalMode.HYBRID,
                        List.of(project, incident, alternative)),
                new ExhaustiveSelectionStrategy());

        PortfolioSelectionResult result = service.select(target);
        PortfolioSelectionResponse first = new PortfolioSelectionResponseMapper().map(result, target);
        PortfolioSelectionResponse second = new PortfolioSelectionResponseMapper().map(result, target);

        assertThat(first.getSelectionId()).isEqualTo(second.getSelectionId());
        assertThat(first.getSelectionMode()).isEqualTo("EXHAUSTIVE");
        assertThat(first.getStatus()).isEqualTo(PortfolioSelectionStatus.READY);
        assertThat(first.getCoverage()).extracting(value -> value.getCapabilityCode())
                .containsExactly("INCIDENT_ANALYSIS", "JAVA");
        assertThat(first.getItems()).allSatisfy(item -> {
            assertThat(item.getTitle()).isNotBlank();
            assertThat(item.getSummary()).isNotBlank();
            assertThat(item.getRoute()).startsWith("/");
            assertThat(item.getSelectionReason()).isNotBlank();
        });
        assertThat(first.getItems()).flatExtracting(item -> item.getEvidenceRefs())
                .extracting(reference -> reference.getEvidenceId())
                .contains("EVIDENCE-01", "EVIDENCE-02")
                .doesNotContain("EVIDENCE-PRIVATE");
        assertThat(first.getComplementarity()).hasSize(1);
        assertThat(first.getAlternatives()).extracting(value -> value.getSubjectId())
                .containsExactly("CASE-03");
        assertThat(first.getDegradation()).isNull();
    }

    @Test
    void reportsUncoveredCapabilityAsStructuredInsufficiency() {
        SelectionTarget target = new SelectionTarget(
                null, "HR", Set.of("UNKNOWN_CAPABILITY"), null, 2);
        PortfolioSelectionService service = new PortfolioSelectionService(
                (ignored, limit) -> new CandidateRetrievalResult(
                        "2026-07-30.1",
                        RetrievalMode.FTS_ONLY,
                        List.of(
                                candidate("PROJECT-01", PortfolioSubjectKind.PROJECT,
                                        Set.of("JAVA"), List.of(), 0.9),
                                candidate("CASE-02", PortfolioSubjectKind.CASE,
                                        Set.of("DELIVERY"), List.of(), 0.8))),
                new ExhaustiveSelectionStrategy());

        PortfolioSelectionResponse response = new PortfolioSelectionResponseMapper()
                .map(service.select(target), target);

        assertThat(response.getStatus()).isEqualTo(PortfolioSelectionStatus.INSUFFICIENT);
        assertThat(response.getDegradation().getCode())
                .isEqualTo("CAPABILITY_COVERAGE_INCOMPLETE");
        assertThat(response.getCoverage()).extracting(value -> value.getCapabilityCode())
                .containsExactly("UNKNOWN_CAPABILITY");
        assertThat(response.getCoverage().get(0).getCoveredBySubjectIds()).isEmpty();
    }

    private SelectionCandidate candidate(
            String id,
            PortfolioSubjectKind kind,
            Set<String> capabilities,
            List<EvidenceReference> evidence,
            double fit) {
        return new SelectionCandidate(
                id,
                kind,
                "Title " + id,
                "Summary " + id,
                (kind == PortfolioSubjectKind.PROJECT ? "/projects/" : "/cases/") + id.toLowerCase(),
                "JAVA_BACKEND",
                capabilities,
                evidence,
                fit,
                1.0,
                0.0);
    }
}
