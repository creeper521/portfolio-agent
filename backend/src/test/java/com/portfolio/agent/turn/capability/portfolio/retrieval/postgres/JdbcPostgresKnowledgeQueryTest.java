package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres;

import com.portfolio.agent.infrastructure.retrieval.EmbeddingVector;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.ActiveRelease;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.PostgresSelectionQuery;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.EvidenceReference;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.PortfolioSubjectKind;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.PostgresSelectionRow;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.SelectionTarget;
import com.portfolio.agent.turn.capability.portfolio.AuthorizedSubjectScope;
import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceInvocation;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerAchievementStatus;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimProjection;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimVerificationStatus;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerContributionType;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerMateriality;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerVerificationBasis;
import com.portfolio.agent.turn.capability.portfolio.retrieval.CorpusBackend;
import com.portfolio.agent.turn.capability.portfolio.retrieval.RetrievalRequest;
import com.portfolio.agent.turn.capability.portfolio.retrieval.SearchStrategy;
import com.portfolio.agent.turn.planning.GoalSubjectReference;
import com.portfolio.agent.turn.planning.SemanticTask;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcPostgresKnowledgeQueryTest {

    @Test
    void exactScopeUsesStableIdsAndFiltersToTheInvocationClaimProfile() {
        RecordingSelectionQuery selectionQuery = new RecordingSelectionQuery();
        JdbcPostgresKnowledgeQuery query = new JdbcPostgresKnowledgeQuery(
                selectionQuery,
                text -> new EmbeddingVector(new float[]{0.1f, 0.2f}),
                (releaseId, subjectIds) -> List.of(
                        row("claim-1", AnswerClaimCategory.VERIFICATION, "evidence-1"),
                        row("claim-2", AnswerClaimCategory.OUTCOME, "evidence-2")));

        PostgresKnowledgeQueryResult result = query.retrieve(
                exactInvocation(),
                new RetrievalRequest(CorpusBackend.POSTGRESQL, SearchStrategy.EXACT));

        assertThat(selectionQuery.exactSubjectIds).containsExactly(List.of("project-1"));
        assertThat(selectionQuery.exactTargets).singleElement().satisfies(target -> {
            assertThat(target.getCareerTrack()).isNull();
            assertThat(target.getCapabilityCodes()).isEmpty();
            assertThat(target.getAudienceRole()).isEqualTo("PORTFOLIO_RETRIEVAL");
        });
        assertThat(result.getCandidates().getReleaseVersion()).isEqualTo("public-1");
        assertThat(result.getPassages())
                .extracting(PostgresKnowledgePassageRow::getClaimId)
                .containsExactly("claim-1");
    }

    @Test
    void recommendationUsesTypedConstraintsThenAddsBroadFallbackCandidates() {
        RecordingSelectionQuery selectionQuery = new RecordingSelectionQuery();
        JdbcPostgresKnowledgeQuery query = new JdbcPostgresKnowledgeQuery(
                selectionQuery,
                text -> new EmbeddingVector(new float[]{0.1f, 0.2f}),
                (releaseId, subjectIds) -> List.of());
        PortfolioEvidenceInvocation invocation = new PortfolioEvidenceInvocation(
                SemanticTask.Type.PORTFOLIO_RECOMMEND,
                AuthorizedSubjectScope.allPublished("public-1"),
                List.of(PortfolioEvidenceInvocation.FacetProfile.RECOMMENDATION), List.of(),
                com.portfolio.agent.turn.planning.UserGoalProposal.Depth.STANDARD,
                2, Set.of("CAREER_TRACK_JAVA_BACKEND", "CAPABILITY_SQL"),
                "public-1", CorpusBackend.POSTGRESQL, SearchStrategy.KEYWORD,
                CorpusBackend.BUNDLE, SearchStrategy.KEYWORD);

        PostgresKnowledgeQueryResult result = query.retrieve(
                invocation,
                new RetrievalRequest(CorpusBackend.POSTGRESQL, SearchStrategy.KEYWORD));

        assertThat(selectionQuery.searchTargets).hasSize(2);
        assertThat(selectionQuery.searchTargets.getFirst()).satisfies(target -> {
            assertThat(target.getCareerTrack()).isEqualTo("JAVA_BACKEND");
            assertThat(target.getCapabilityCodes()).containsExactly("SQL");
        });
        assertThat(selectionQuery.searchTargets.get(1)).satisfies(target -> {
            assertThat(target.getCareerTrack()).isNull();
            assertThat(target.getCapabilityCodes()).isEmpty();
        });
        assertThat(result.getCandidates().getCandidates())
                .extracting(value -> value.getSubjectId())
                .containsExactly("project-match", "project-fallback");
    }

    private PortfolioEvidenceInvocation exactInvocation() {
        GoalSubjectReference subject = new GoalSubjectReference(
                GoalSubjectReference.Kind.PROJECT,
                "project-1",
                GoalSubjectReference.Basis.CONTINUATION,
                null);
        return new PortfolioEvidenceInvocation(
                SemanticTask.Type.PORTFOLIO_FACT,
                AuthorizedSubjectScope.exact(List.of(subject), "public-1"),
                List.of(PortfolioEvidenceInvocation.FacetProfile.VERIFICATION),
                List.of(),
                "public-1",
                CorpusBackend.POSTGRESQL,
                SearchStrategy.EXACT,
                CorpusBackend.BUNDLE,
                SearchStrategy.EXACT);
    }

    private PostgresKnowledgePassageRow row(
            String claimId,
            AnswerClaimCategory category,
            String evidenceId) {
        String statement = "Verified public statement";
        return new PostgresKnowledgePassageRow(
                "project-1",
                statement,
                new AnswerClaimProjection(
                        claimId,
                        category,
                        statement,
                        "Public verification detail",
                        AnswerAchievementStatus.IMPLEMENTED_TESTED,
                        AnswerContributionType.PRIMARY,
                        AnswerVerificationBasis.EVIDENCE_SUPPORTED,
                        AnswerClaimVerificationStatus.VERIFIED,
                        AnswerMateriality.KEY,
                        List.of("POSTGRESQL"),
                        List.of(evidenceId)),
                List.of(new EvidenceReference(
                        claimId, evidenceId, evidenceId, "Approved evidence",
                        "DOCUMENT", "APPROVED")));
    }

    private static final class RecordingSelectionQuery implements PostgresSelectionQuery {
        private final List<List<String>> exactSubjectIds = new ArrayList<>();
        private final List<SelectionTarget> exactTargets = new ArrayList<>();
        private final List<SelectionTarget> searchTargets = new ArrayList<>();

        @Override
        public ActiveRelease activeRelease() {
            return new ActiveRelease("release-id", "public-1");
        }

        @Override
        public List<PostgresSelectionRow> searchFts(
                String releaseId, SelectionTarget target, int limit) {
            searchTargets.add(target);
            return target.getCareerTrack() == null
                    ? List.of(selectionRow(
                    "project-fallback", "AGENT", Set.of("AGENT")))
                    : List.of(selectionRow(
                    "project-match", "JAVA_BACKEND", Set.of("SQL")));
        }

        private PostgresSelectionRow selectionRow(
                String subjectId, String careerTrack, Set<String> capabilities) {
            return new PostgresSelectionRow(
                    subjectId, PortfolioSubjectKind.PROJECT, subjectId,
                    "Public summary", "/projects/" + subjectId,
                    careerTrack, capabilities,
                    List.of(new EvidenceReference(
                            "claim-" + subjectId, "evidence-" + subjectId,
                            "Approved evidence")), 1.0);
        }

        @Override
        public List<PostgresSelectionRow> searchVector(
                String releaseId, float[] embedding, SelectionTarget target, int limit) {
            throw new AssertionError("exact scope must not search");
        }

        @Override
        public List<PostgresSelectionRow> findByIds(
                String releaseId,
                List<String> subjectIds,
                SelectionTarget target) {
            exactSubjectIds.add(List.copyOf(subjectIds));
            exactTargets.add(target);
            return List.of(new PostgresSelectionRow(
                    "project-1",
                    PortfolioSubjectKind.PROJECT,
                    "PostgreSQL audit",
                    "Public summary",
                    "/projects/project-1",
                    "BACKEND",
                    Set.of("POSTGRESQL"),
                    List.of(new EvidenceReference(
                            "claim-1", "evidence-1", "Approved evidence")),
                    1.0));
        }
    }
}
