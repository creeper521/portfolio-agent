package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres;

import com.portfolio.agent.infrastructure.retrieval.LocalEmbeddingPort;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.ActiveRelease;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.JdbcPostgresSelectionQuery;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.PostgresHybridCandidateRetriever;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.PostgresSelectionQuery;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.CandidateRetrievalResult;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.PostgresSelectionRow;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.RetrievalMode;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.SelectionCandidate;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.SelectionTarget;
import com.portfolio.agent.turn.capability.portfolio.AuthorizedSubjectScope;
import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceInvocation;
import com.portfolio.agent.turn.capability.portfolio.knowledge.AnswerClaimCategory;
import com.portfolio.agent.turn.capability.portfolio.retrieval.RetrievalRequest;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;

/** PostgreSQL 公开检索查询；输入只接受最终 Turn 调用与检索策略。 */
public final class JdbcPostgresKnowledgeQuery implements PostgresKnowledgeQuery {

    private static final String FIXED_AUDIENCE_ROLE = "PORTFOLIO_RETRIEVAL";
    private static final String CONTROLLED_QUERY = "portfolio-profile-v1";
    private static final int FIXED_TARGET_SIZE = 3;
    private static final int MAX_SUBJECTS = 50;

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
    public PostgresKnowledgeQueryResult retrieve(
            PortfolioEvidenceInvocation invocation,
            RetrievalRequest request) {
        Objects.requireNonNull(invocation, "invocation");
        Objects.requireNonNull(request, "request");
        SelectionTarget target = new SelectionTarget(
                null, FIXED_AUDIENCE_ROLE, Set.of(), CONTROLLED_QUERY, FIXED_TARGET_SIZE);
        ActiveRelease release = selectionQuery.activeRelease();
        if (invocation.getSubjectScope().getMode() == AuthorizedSubjectScope.Mode.EXACT) {
            return retrieveExact(release, invocation, target);
        }
        CandidateRetrievalResult candidates = candidateRetriever.retrieve(
                release, target, MAX_SUBJECTS, request.getStrategy());
        List<String> subjectIds = candidates.getCandidates().stream()
                .map(SelectionCandidate::getSubjectId)
                .toList();
        return new PostgresKnowledgeQueryResult(
                candidates,
                passageQuery.findPassages(release.getReleaseId(), subjectIds));
    }

    private PostgresKnowledgeQueryResult retrieveExact(
            ActiveRelease release,
            PortfolioEvidenceInvocation invocation,
            SelectionTarget target) {
        List<String> subjectIds = invocation.getSubjectScope().getSubjects().stream()
                .map(reference -> reference.getReference())
                .toList();
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
        List<AnswerClaimCategory> categories = categories(invocation);
        List<PostgresKnowledgePassageRow> passages = passageQuery
                .findPassages(release.getReleaseId(), subjectIds).stream()
                .filter(row -> categories.isEmpty() || categories.contains(row.getClaimCategory()))
                .limit(50)
                .toList();
        return new PostgresKnowledgeQueryResult(
                new CandidateRetrievalResult(
                        release.getReleaseVersion(), RetrievalMode.FTS_ONLY, candidates),
                passages);
    }

    private SelectionCandidate exactCandidate(PostgresSelectionRow row) {
        return new SelectionCandidate(
                row.getSubjectId(), row.getSubjectKind(), row.getTitle(), row.getSummary(),
                row.getRoute(), row.getCareerTrack(), row.getCapabilityCodes(),
                row.getEvidenceReferences(), 1.0d, row.getEvidenceQuality(), 0.0d);
    }

    private List<AnswerClaimCategory> categories(PortfolioEvidenceInvocation invocation) {
        LinkedHashSet<AnswerClaimCategory> categories = new LinkedHashSet<>();
        invocation.getFacets().forEach(facet -> categories.addAll(switch (facet) {
            case BACKGROUND -> List.of(AnswerClaimCategory.BACKGROUND);
            case RESPONSIBILITY -> List.of(AnswerClaimCategory.RESPONSIBILITY);
            case IMPLEMENTATION -> List.of(AnswerClaimCategory.IMPLEMENTATION);
            case TECHNICAL_DECISION -> List.of(AnswerClaimCategory.TECHNICAL_DECISION);
            case VERIFICATION -> List.of(AnswerClaimCategory.VERIFICATION);
            case OUTCOME -> List.of(AnswerClaimCategory.OUTCOME);
            case LIMITATION -> List.of(AnswerClaimCategory.LIMITATION);
            case RECOMMENDATION -> List.of(
                    AnswerClaimCategory.BACKGROUND,
                    AnswerClaimCategory.IMPLEMENTATION,
                    AnswerClaimCategory.VERIFICATION,
                    AnswerClaimCategory.OUTCOME,
                    AnswerClaimCategory.TECHNICAL_DECISION);
        }));
        invocation.getDimensions().forEach(dimension -> categories.add(switch (dimension) {
            case "ARCHITECTURE", "TECHNICAL_DECISION" -> AnswerClaimCategory.TECHNICAL_DECISION;
            case "IMPLEMENTATION" -> AnswerClaimCategory.IMPLEMENTATION;
            case "IMPACT", "OUTCOME" -> AnswerClaimCategory.OUTCOME;
            case "RISKS", "LIMITATION" -> AnswerClaimCategory.LIMITATION;
            default -> AnswerClaimCategory.VERIFICATION;
        }));
        return List.copyOf(categories);
    }
}
