package com.portfolio.agent.answer.intelligence.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.agent.answer.domain.EmbeddingVector;
import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalRequest;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import com.portfolio.agent.selection.adapter.postgres.ActiveRelease;
import com.portfolio.agent.selection.adapter.postgres.PostgresSelectionQuery;
import com.portfolio.agent.selection.domain.PortfolioSubjectKind;
import com.portfolio.agent.selection.domain.EvidenceReference;
import com.portfolio.agent.selection.domain.PostgresSelectionRow;
import com.portfolio.agent.selection.domain.SelectionTarget;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JdbcPostgresKnowledgeQueryTest {

    @Test
    void reusesPinnedHybridSelectionQueryWithAFixedRetrievalTarget() {
        RecordingSelectionQuery selectionQuery = new RecordingSelectionQuery();
        JdbcPostgresKnowledgeQuery query = new JdbcPostgresKnowledgeQuery(
                selectionQuery,
                text -> new EmbeddingVector(new float[]{0.1f, 0.2f}),
                (releaseId, subjectIds) -> List.of(new PostgresKnowledgePassageRow(
                        "project-1", "claim-1", "Actual verified PostgreSQL claim",
                        List.of(new EvidenceReference(
                                "claim-1", "evidence-1", "Approved evidence", "APPROVED")))));

        PortfolioRetrievalRequest request = new PortfolioRetrievalRequest(
                "PostgreSQL audit", PortfolioTaskMode.FACT_LOOKUP,
                new PortfolioConditions("BACKEND", null, Set.of("POSTGRESQL"), null, null), 20);

        PostgresKnowledgeQueryResult result = query.retrieve(request);

        assertThat(result.getCandidates().getReleaseVersion()).isEqualTo("public-2026-07-31");
        assertThat(result.getCandidates().getCandidates())
                .extracting(candidate -> candidate.getSubjectId())
                .containsExactly("project-1");
        assertThat(result.getPassages()).singleElement().satisfies(passage -> {
            assertThat(passage.getContent()).isEqualTo("Actual verified PostgreSQL claim");
            assertThat(passage.getContent()).isNotEqualTo("Public summary");
        });
        assertThat(selectionQuery.releaseIds).containsOnly("release-id");
        assertThat(selectionQuery.targets).allSatisfy(target -> {
            assertThat(target.getCareerTrack()).isEqualTo("BACKEND");
            assertThat(target.getCapabilityCodes()).containsExactly("POSTGRESQL");
            assertThat(target.getAudienceRole()).isEqualTo("PORTFOLIO_RETRIEVAL");
        });
    }

    @Test
    void validatesReturnedContextByExactIdsInTheCurrentRelease() {
        RecordingSelectionQuery selectionQuery = new RecordingSelectionQuery();
        JdbcPostgresKnowledgeQuery query = new JdbcPostgresKnowledgeQuery(
                selectionQuery,
                text -> new EmbeddingVector(new float[]{0.1f, 0.2f}),
                (releaseId, subjectIds) -> List.of(
                        new PostgresKnowledgePassageRow(
                                "project-1", "claim-1", "PostgreSQL verification details",
                                AnswerClaimCategory.VERIFICATION,
                                List.of(new EvidenceReference(
                                        "claim-1", "evidence-1", "Approved evidence", "APPROVED"))),
                        new PostgresKnowledgePassageRow(
                                "project-1", "claim-2", "PostgreSQL verification outcome",
                                AnswerClaimCategory.OUTCOME,
                                List.of(new EvidenceReference(
                                        "claim-2", "evidence-2", "Approved evidence", "APPROVED")))));

        PostgresKnowledgeQueryResult result = query.retrieve(
                PortfolioRetrievalRequest.contextValidation(
                        new PortfolioConditions(
                                "BACKEND", "INTERVIEWER", Set.of("POSTGRESQL"), null, 2),
                        List.of("project-1")));

        assertThat(selectionQuery.exactSubjectIds).containsExactly(List.of("project-1"));
        assertThat(selectionQuery.exactTargets).singleElement().satisfies(target -> {
            assertThat(target.getCareerTrack()).isEqualTo("BACKEND");
            assertThat(target.getCapabilityCodes()).containsExactly("POSTGRESQL");
        });
        assertThat(selectionQuery.targets).isEmpty();
        assertThat(result.getCandidates().getCandidates())
                .extracting(candidate -> candidate.getSubjectId())
                .containsExactly("project-1");
    }

    @Test
    void subjectScopeUsesTheSameStableIdBoundaryForPostgresRetrieval() {
        RecordingSelectionQuery selectionQuery = new RecordingSelectionQuery();
        JdbcPostgresKnowledgeQuery query = new JdbcPostgresKnowledgeQuery(
                selectionQuery,
                text -> new EmbeddingVector(new float[]{0.1f, 0.2f}),
                (releaseId, subjectIds) -> List.of(
                        new PostgresKnowledgePassageRow(
                                "project-1", "claim-1", "PostgreSQL verification details",
                                AnswerClaimCategory.VERIFICATION,
                                List.of(new EvidenceReference(
                                        "claim-1", "evidence-1", "Approved evidence", "APPROVED"))),
                        new PostgresKnowledgePassageRow(
                                "project-1", "claim-2", "PostgreSQL verification outcome",
                                AnswerClaimCategory.OUTCOME,
                                List.of(new EvidenceReference(
                                        "claim-2", "evidence-2", "Approved evidence", "APPROVED")))));

        PostgresKnowledgeQueryResult result = query.retrieve(
                PortfolioRetrievalRequest.subjectScope(
                        "PostgreSQL verification",
                        PortfolioTaskMode.FACT_LOOKUP,
                        PortfolioConditions.empty(),
                        "project-1",
                        List.of(AnswerClaimCategory.VERIFICATION)));

        assertThat(selectionQuery.exactSubjectIds).containsExactly(List.of("project-1"));
        assertThat(selectionQuery.targets).isEmpty();
        assertThat(result.getCandidates().getCandidates())
                .extracting(candidate -> candidate.getSubjectId())
                .containsExactly("project-1");
        assertThat(result.getPassages())
                .extracting(PostgresKnowledgePassageRow::getClaimId)
                .containsExactly("claim-1");
    }

    @Test
    void presetContractUsesExactPassagesAndNeverCallsRelevantPassageRetrieval() {
        RecordingSelectionQuery selectionQuery = new RecordingSelectionQuery();
        RecordingPassageQuery passageQuery = new RecordingPassageQuery();
        JdbcPostgresKnowledgeQuery query = new JdbcPostgresKnowledgeQuery(
                selectionQuery,
                text -> { throw new AssertionError("contract retrieval must not embed the query"); },
                passageQuery);

        PostgresKnowledgeQueryResult result = query.retrieve(
                PortfolioRetrievalRequest.contractScope(
                        "unrelated text must not affect the contract", "project-1", List.of("claim-1")));

        assertThat(selectionQuery.exactSubjectIds).containsExactly(List.of("project-1"));
        assertThat(passageQuery.exactRequests).containsExactly(List.of("release-id", List.of("project-1")));
        assertThat(passageQuery.relevantCallCount).isZero();
        assertThat(result.getPassages()).extracting(PostgresKnowledgePassageRow::getClaimId)
                .containsExactly("claim-1");
    }

    @Test
    void validatesAnEmptyRecommendationWithoutIssuingAnArrayQuery() {
        RecordingSelectionQuery selectionQuery = new RecordingSelectionQuery();
        JdbcPostgresKnowledgeQuery query = new JdbcPostgresKnowledgeQuery(
                selectionQuery,
                text -> new EmbeddingVector(new float[]{0.1f, 0.2f}),
                (releaseId, subjectIds) -> List.of());

        PostgresKnowledgeQueryResult result = query.retrieve(
                PortfolioRetrievalRequest.contextValidation(
                        new PortfolioConditions(
                                "BACKEND", "INTERVIEWER", Set.of("POSTGRESQL"), null, 2),
                        List.of()));

        assertThat(selectionQuery.exactSubjectIds).isEmpty();
        assertThat(result.getCandidates().getReleaseVersion()).isEqualTo("public-2026-07-31");
        assertThat(result.getCandidates().getCandidates()).isEmpty();
        assertThat(result.getPassages()).isEmpty();
    }

    private static final class RecordingSelectionQuery implements PostgresSelectionQuery {

        private final List<String> releaseIds = new java.util.ArrayList<>();
        private final List<SelectionTarget> targets = new java.util.ArrayList<>();
        private final List<List<String>> exactSubjectIds = new java.util.ArrayList<>();
        private final List<SelectionTarget> exactTargets = new java.util.ArrayList<>();

        @Override
        public ActiveRelease activeRelease() {
            return new ActiveRelease("release-id", "public-2026-07-31");
        }

        @Override
        public List<PostgresSelectionRow> searchFts(String releaseId, SelectionTarget target, int limit) {
            releaseIds.add(releaseId);
            targets.add(target);
            return List.of(row());
        }

        @Override
        public List<PostgresSelectionRow> searchVector(
                String releaseId, float[] embedding, SelectionTarget target, int limit) {
            releaseIds.add(releaseId);
            targets.add(target);
            return List.of(row());
        }

        @Override
        public List<PostgresSelectionRow> findByIds(
                String releaseId,
                List<String> subjectIds,
                SelectionTarget target) {
            releaseIds.add(releaseId);
            exactSubjectIds.add(List.copyOf(subjectIds));
            exactTargets.add(target);
            return List.of(row());
        }

        private PostgresSelectionRow row() {
            return new PostgresSelectionRow(
                    "project-1", PortfolioSubjectKind.PROJECT, "PostgreSQL audit", "Public summary",
                    "/projects/project-1", "BACKEND", Set.of("POSTGRESQL"),
                    List.of(new EvidenceReference("claim-1", "evidence-1", "Approved evidence")), 1.0);
        }
    }

    private static final class RecordingPassageQuery implements PostgresFactPassageQuery {

        private final List<List<Object>> exactRequests = new java.util.ArrayList<>();
        private int relevantCallCount;

        @Override
        public List<PostgresKnowledgePassageRow> findPassages(String releaseId, List<String> subjectIds) {
            exactRequests.add(List.of(releaseId, List.copyOf(subjectIds)));
            return List.of(
                    new PostgresKnowledgePassageRow(
                            "project-1", "claim-1", "Contract claim",
                            List.of(new EvidenceReference(
                                    "claim-1", "evidence-1", "Approved evidence", "APPROVED"))),
                    new PostgresKnowledgePassageRow(
                            "project-1", "claim-2", "Unrequested claim",
                            List.of(new EvidenceReference(
                                    "claim-2", "evidence-2", "Approved evidence", "APPROVED"))));
        }

        @Override
        public List<PostgresKnowledgePassageRow> findRelevantPassages(
                String releaseId,
                List<String> subjectIds,
                String query,
                List<AnswerClaimCategory> preferredClaimCategories,
                int limit) {
            relevantCallCount++;
            throw new AssertionError("preset contract must not call relevant passage retrieval");
        }
    }
}
