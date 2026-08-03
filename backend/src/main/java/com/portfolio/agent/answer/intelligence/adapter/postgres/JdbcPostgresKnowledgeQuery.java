package com.portfolio.agent.answer.intelligence.adapter.postgres;

import com.portfolio.agent.answer.gateway.LocalEmbeddingPort;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalRequest;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalStrategy;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetrievalException;
import com.portfolio.agent.selection.adapter.postgres.JdbcPostgresSelectionQuery;
import com.portfolio.agent.selection.adapter.postgres.PostgresHybridCandidateRetriever;
import com.portfolio.agent.selection.adapter.postgres.PostgresSelectionQuery;
import com.portfolio.agent.selection.domain.CandidateRetrievalResult;
import com.portfolio.agent.selection.domain.RetrievalMode;
import com.portfolio.agent.selection.domain.SelectionCandidate;
import com.portfolio.agent.selection.domain.PostgresSelectionRow;
import com.portfolio.agent.selection.domain.SelectionTarget;
import com.portfolio.agent.selection.gateway.CandidateRetrievalException;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcPostgresKnowledgeQuery implements PostgresKnowledgeQuery {

    private static final String FIXED_AUDIENCE_ROLE = "PORTFOLIO_RETRIEVAL";
    private static final int FIXED_TARGET_SIZE = 3;

    private final PostgresSelectionQuery selectionQuery;
    private final PostgresHybridCandidateRetriever candidateRetriever;
    private final PostgresFactPassageQuery passageQuery;

    public JdbcPostgresKnowledgeQuery(JdbcTemplate jdbcTemplate, LocalEmbeddingPort embeddingPort) {
        this(
                new JdbcPostgresSelectionQuery(Objects.requireNonNull(jdbcTemplate, "jdbcTemplate")),
                embeddingPort,
                new JdbcPostgresFactPassageQuery(jdbcTemplate));
    }

    public JdbcPostgresKnowledgeQuery(
            PostgresSelectionQuery selectionQuery,
            LocalEmbeddingPort embeddingPort,
            PostgresFactPassageQuery passageQuery) {
        this.selectionQuery = Objects.requireNonNull(selectionQuery, "selectionQuery");
        this.candidateRetriever = new PostgresHybridCandidateRetriever(
                selectionQuery,
                Objects.requireNonNull(embeddingPort, "embeddingPort"));
        this.passageQuery = Objects.requireNonNull(passageQuery, "passageQuery");
    }

    @Override
    public PostgresKnowledgeQueryResult retrieve(PortfolioRetrievalRequest request) {
        Objects.requireNonNull(request, "request");
        SelectionTarget target = new SelectionTarget(
                request.getConditions().getCareerTrack(),
                FIXED_AUDIENCE_ROLE,
                request.getConditions().getCapabilityCodes(),
                request.getQuery(),
                FIXED_TARGET_SIZE);
        try {
            com.portfolio.agent.selection.adapter.postgres.ActiveRelease release =
                    selectionQuery.activeRelease();
            if (request.isExactPortfolioLookup()) {
                return retrieveExact(release, request, target);
            }
            CandidateRetrievalResult candidates = candidateRetriever.retrieve(
                    release, target, request.getLimit());
            List<String> subjectIds = candidates.getCandidates().stream()
                    .map(candidate -> candidate.getSubjectId())
                    .toList();
            return new PostgresKnowledgeQueryResult(
                    candidates,
                    passageQuery.findPassages(release.getReleaseId(), subjectIds));
        } catch (CandidateRetrievalException exception) {
            throw new PortfolioRetrievalException(
                    PostgresRetrievalFailureClassifier.classify(exception),
                    "PostgreSQL public retrieval failed",
                    exception);
        } catch (DataAccessException exception) {
            throw new PortfolioRetrievalException(
                    PostgresRetrievalFailureClassifier.classify(exception),
                    "PostgreSQL public retrieval failed",
                    exception);
        }
    }

    private PostgresKnowledgeQueryResult retrieveExact(
            com.portfolio.agent.selection.adapter.postgres.ActiveRelease release,
            PortfolioRetrievalRequest request,
            SelectionTarget target) {
        List<String> subjectIds = request.getRequiredPortfolioIds();
        if (subjectIds.isEmpty()) {
            return new PostgresKnowledgeQueryResult(
                    new CandidateRetrievalResult(
                            release.getReleaseVersion(), RetrievalMode.FTS_ONLY, List.of()),
                    List.of());
        }
        List<SelectionCandidate> candidates = selectionQuery.findByIds(
                        release.getReleaseId(), subjectIds, target).stream()
                .map(this::exactCandidate)
                .toList();
        CandidateRetrievalResult candidateResult = new CandidateRetrievalResult(
                release.getReleaseVersion(), RetrievalMode.FTS_ONLY, candidates);
        List<PostgresKnowledgePassageRow> passages = request.getStrategy()
                == PortfolioRetrievalStrategy.CONTEXT_VALIDATION
                ? passageQuery.findPassages(release.getReleaseId(), subjectIds)
                : request.getStrategy() == PortfolioRetrievalStrategy.REFERENCE_SCOPED
                ? passageQuery.findPassages(release.getReleaseId(), subjectIds).stream()
                        .filter(row -> request.getRequiredClaimIds().isEmpty()
                                || request.getRequiredClaimIds().contains(row.getClaimId()))
                        .filter(row -> request.getPreferredClaimCategories().isEmpty()
                                || request.getPreferredClaimCategories().contains(
                                        row.getClaimCategory()))
                        .limit(request.getLimit())
                        .toList()
                : passageQuery.findRelevantPassages(
                        release.getReleaseId(), subjectIds,
                        request.getQuery(), request.getPreferredClaimCategories(),
                        request.getLimit());
        return new PostgresKnowledgeQueryResult(candidateResult, passages);
    }

    private SelectionCandidate exactCandidate(PostgresSelectionRow row) {
        return new SelectionCandidate(
                row.getSubjectId(), row.getSubjectKind(), row.getTitle(), row.getSummary(),
                row.getRoute(), row.getCareerTrack(), row.getCapabilityCodes(),
                row.getEvidenceReferences(), 1.0d, row.getEvidenceQuality(), 0.0d);
    }
}
