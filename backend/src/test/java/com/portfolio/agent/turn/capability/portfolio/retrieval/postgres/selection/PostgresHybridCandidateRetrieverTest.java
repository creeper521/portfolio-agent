package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.agent.infrastructure.retrieval.EmbeddingVector;
import com.portfolio.agent.turn.capability.portfolio.PortfolioSubjectKind;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.CandidateRetrievalResult;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.EvidenceReference;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.PostgresSelectionRow;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.RetrievalMode;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.SelectionTarget;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PostgresHybridCandidateRetrieverTest {

    @Test
    void fusesFtsAndVectorRanksWithinOneActiveRelease() {
        PostgresSelectionQuery query = new StubQuery(
                List.of(row("PROJECT-01"), row("CASE-02")),
                List.of(row("CASE-02"), row("PROJECT-04")),
                false);
        PostgresHybridCandidateRetriever retriever = new PostgresHybridCandidateRetriever(
                query,
                text -> new EmbeddingVector(new float[]{0.1f, 0.2f}));

        CandidateRetrievalResult result = retriever.retrieve(target(), 12);

        assertThat(result.getReleaseVersion()).isEqualTo("2026-07-30.1");
        assertThat(result.getRetrievalMode()).isEqualTo(RetrievalMode.HYBRID);
        assertThat(result.getCandidates()).extracting(candidate -> candidate.getSubjectId())
                .containsExactly("CASE-02", "PROJECT-01", "PROJECT-04");
    }

    @Test
    void degradesToFtsOnlyWhenVectorQueryFailsAndTargetIsStructured() {
        PostgresSelectionQuery query = new StubQuery(
                List.of(row("PROJECT-01")),
                List.of(),
                true);
        PostgresHybridCandidateRetriever retriever = new PostgresHybridCandidateRetriever(
                query,
                text -> new EmbeddingVector(new float[]{0.1f, 0.2f}));

        CandidateRetrievalResult result = retriever.retrieve(target(), 12);

        assertThat(result.getRetrievalMode()).isEqualTo(RetrievalMode.FTS_ONLY);
        assertThat(result.getCandidates()).extracting(candidate -> candidate.getSubjectId())
                .containsExactly("PROJECT-01");
    }

    @Test
    void preservesPublicDisplayMetadataAndApprovedEvidenceWhilePinningOneRelease() {
        PostgresSelectionRow row = new PostgresSelectionRow(
                "PROJECT-01",
                PortfolioSubjectKind.PROJECT,
                "SQL audit",
                "Audited production SQL",
                "/projects/sql-audit",
                "JAVA_BACKEND",
                Set.of("JAVA"),
                List.of(new EvidenceReference("CLAIM-01", "EVIDENCE-01", "Public report")),
                1.0);
        ReleasePinningQuery query = new ReleasePinningQuery(row);
        PostgresHybridCandidateRetriever retriever = new PostgresHybridCandidateRetriever(
                query,
                text -> new EmbeddingVector(new float[]{0.1f, 0.2f}));

        CandidateRetrievalResult result = retriever.retrieve(target(), 12);

        assertThat(query.seenReleaseIds).containsExactly("release-id", "release-id");
        assertThat(result.getCandidates()).singleElement().satisfies(candidate -> {
            assertThat(candidate.getTitle()).isEqualTo("SQL audit");
            assertThat(candidate.getSummary()).isEqualTo("Audited production SQL");
            assertThat(candidate.getRoute()).isEqualTo("/projects/sql-audit");
            assertThat(candidate.getEvidenceReferences()).singleElement().satisfies(reference -> {
                assertThat(reference.getClaimId()).isEqualTo("CLAIM-01");
                assertThat(reference.getEvidenceId()).isEqualTo("EVIDENCE-01");
                assertThat(reference.getLabel()).isEqualTo("Public report");
            });
        });
    }

    private SelectionTarget target() {
        return new SelectionTarget(
                "JAVA_BACKEND",
                "TECH_INTERVIEWER",
                Set.of("JAVA"),
                null,
                Set.of(PortfolioSubjectKind.PROJECT, PortfolioSubjectKind.CASE),
                3);
    }

    private PostgresSelectionRow row(String subjectId) {
        return new PostgresSelectionRow(
                subjectId,
                subjectId.startsWith("PROJECT")
                        ? PortfolioSubjectKind.PROJECT
                        : PortfolioSubjectKind.CASE,
                "JAVA_BACKEND",
                Set.of("JAVA"),
                0.9);
    }

    private static final class StubQuery implements PostgresSelectionQuery {

        private final List<PostgresSelectionRow> ftsRows;
        private final List<PostgresSelectionRow> vectorRows;
        private final boolean vectorFailure;

        private StubQuery(
                List<PostgresSelectionRow> ftsRows,
                List<PostgresSelectionRow> vectorRows,
                boolean vectorFailure) {
            this.ftsRows = ftsRows;
            this.vectorRows = vectorRows;
            this.vectorFailure = vectorFailure;
        }

        @Override
        public ActiveRelease activeRelease() {
            return new ActiveRelease("release-id", "2026-07-30.1");
        }

        @Override
        public List<PostgresSelectionRow> searchFts(
                String releaseId,
                SelectionTarget target,
                int limit) {
            return ftsRows;
        }

        @Override
        public List<PostgresSelectionRow> searchVector(
                String releaseId,
                float[] embedding,
                SelectionTarget target,
                int limit) {
            if (vectorFailure) {
                throw new IllegalStateException("vector unavailable");
            }
            return vectorRows;
        }
    }

    private static final class ReleasePinningQuery implements PostgresSelectionQuery {

        private final PostgresSelectionRow row;
        private final java.util.List<String> seenReleaseIds = new java.util.ArrayList<>();

        private ReleasePinningQuery(PostgresSelectionRow row) {
            this.row = row;
        }

        @Override
        public ActiveRelease activeRelease() {
            return new ActiveRelease("release-id", "2026-07-30.1");
        }

        @Override
        public List<PostgresSelectionRow> searchFts(
                String releaseId,
                SelectionTarget target,
                int limit) {
            seenReleaseIds.add(releaseId);
            return List.of(row);
        }

        @Override
        public List<PostgresSelectionRow> searchVector(
                String releaseId,
                float[] embedding,
                SelectionTarget target,
                int limit) {
            seenReleaseIds.add(releaseId);
            return List.of(row);
        }
    }
}
