package com.portfolio.agent.answer.intelligence.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalRequest;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetrievalException;
import com.portfolio.agent.selection.gateway.CandidateRetrievalException;
import com.portfolio.agent.selection.domain.CandidateRetrievalResult;
import com.portfolio.agent.selection.domain.EvidenceReference;
import com.portfolio.agent.selection.domain.PortfolioSubjectKind;
import com.portfolio.agent.selection.domain.RetrievalMode;
import com.portfolio.agent.selection.domain.SelectionCandidate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class PostgresPortfolioRetrieverTest {

    @Test
    void mapsOnlyApprovedEvidenceBackedCandidatesToTheUnifiedRetrievalShape() {
        PostgresPortfolioRetriever retriever = new PostgresPortfolioRetriever(request ->
                new PostgresKnowledgeQueryResult(
                        new CandidateRetrievalResult("public-2026-07-31", RetrievalMode.HYBRID, List.of(
                                candidate("project-1", "claim-1", "evidence-1"))),
                        List.of(new PostgresKnowledgePassageRow(
                                "project-1", "claim-1", "Actual verified PostgreSQL claim",
                                List.of(new EvidenceReference(
                                        "claim-1", "evidence-1", "Approved evidence", "APPROVED"))))));

        assertThat(retriever.retrieve(request()).getSubjects()).singleElement().satisfies(subject -> {
            assertThat(subject.getPortfolioId()).isEqualTo("project-1");
            assertThat(subject.getRoute()).isEqualTo("/projects/project-1");
            assertThat(subject.getCareerTrack()).isEqualTo("BACKEND");
            assertThat(subject.getTargetFit()).isEqualTo(0.61d);
            assertThat(subject.getEvidenceQuality()).isEqualTo(0.83d);
            assertThat(subject.getConflictPenalty()).isEqualTo(0.2d);
        });
        assertThat(retriever.retrieve(request()).getPassages()).singleElement().satisfies(passage -> {
            assertThat(passage.getClaimId()).isEqualTo("claim-1");
            assertThat(passage.getEvidenceIds()).containsExactly("evidence-1");
            assertThat(passage.getEvidenceReferences()).singleElement().satisfies(reference -> {
                assertThat(reference.getLabel()).isEqualTo("Approved evidence");
                assertThat(reference.getPublicStatus()).isEqualTo("APPROVED");
            });
            assertThat(passage.getContent()).isEqualTo("Actual verified PostgreSQL claim");
            assertThat(passage.getContent()).isNotEqualTo("Public summary");
        });
        assertThat(retriever.retrieve(request()).getSource().getAdapterId()).isEqualTo("POSTGRES_PGVECTOR");
    }

    @Test
    void wrapsOnlyDatabaseInfrastructureFailures() {
        PostgresPortfolioRetriever retriever = new PostgresPortfolioRetriever(request -> {
            throw new DataAccessResourceFailureException("database unavailable");
        });

        assertThatThrownBy(() -> retriever.retrieve(request()))
                .isInstanceOf(PortfolioRetrievalException.class)
                .hasCauseInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void mapsExistingPostgresCandidateRetrievalFailuresToTheRetrievalSeamException() {
        PostgresPortfolioRetriever retriever = new PostgresPortfolioRetriever(request -> {
            throw new CandidateRetrievalException("postgres unavailable");
        });

        assertThatThrownBy(() -> retriever.retrieve(request()))
                .isInstanceOf(PortfolioRetrievalException.class)
                .hasCauseInstanceOf(CandidateRetrievalException.class);
    }

    private PortfolioRetrievalRequest request() {
        return new PortfolioRetrievalRequest(
                "PostgreSQL", PortfolioTaskMode.FACT_LOOKUP, PortfolioConditions.empty());
    }

    private SelectionCandidate candidate(String subjectId, String claimId, String evidenceId) {
        return new SelectionCandidate(
                subjectId, PortfolioSubjectKind.PROJECT, "PostgreSQL audit", "Public summary",
                "/projects/" + subjectId, "BACKEND", Set.of("POSTGRESQL"),
                List.of(new EvidenceReference(claimId, evidenceId, "Approved evidence")), 0.61, 0.83, 0.2);
    }
}
