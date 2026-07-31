package com.portfolio.agent.answer.intelligence.adapter.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfolio.agent.answer.domain.EmbeddingVector;
import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalRequest;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import com.portfolio.agent.selection.adapter.postgres.ActiveRelease;
import com.portfolio.agent.selection.adapter.postgres.PostgresSelectionQuery;
import com.portfolio.agent.selection.domain.PortfolioSubjectKind;
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
                selectionQuery, text -> new EmbeddingVector(new float[]{0.1f, 0.2f}));

        PortfolioRetrievalRequest request = new PortfolioRetrievalRequest(
                "PostgreSQL audit", PortfolioTaskMode.FACT_LOOKUP,
                new PortfolioConditions("BACKEND", null, Set.of("POSTGRESQL"), null, null), 20);

        assertThat(query.retrieve(request).getReleaseVersion()).isEqualTo("public-2026-07-31");
        assertThat(query.retrieve(request).getCandidates())
                .extracting(candidate -> candidate.getSubjectId())
                .containsExactly("project-1");
        assertThat(selectionQuery.releaseIds).containsOnly("release-id");
        assertThat(selectionQuery.targets).allSatisfy(target -> {
            assertThat(target.getCareerTrack()).isEqualTo("BACKEND");
            assertThat(target.getCapabilityCodes()).containsExactly("POSTGRESQL");
            assertThat(target.getAudienceRole()).isEqualTo("PORTFOLIO_RETRIEVAL");
        });
    }

    private static final class RecordingSelectionQuery implements PostgresSelectionQuery {

        private final List<String> releaseIds = new java.util.ArrayList<>();
        private final List<SelectionTarget> targets = new java.util.ArrayList<>();

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

        private PostgresSelectionRow row() {
            return new PostgresSelectionRow(
                    "project-1", PortfolioSubjectKind.PROJECT, "PostgreSQL audit", "Public summary",
                    "/projects/project-1", "BACKEND", Set.of("POSTGRESQL"), List.of(), 1.0);
        }
    }
}
