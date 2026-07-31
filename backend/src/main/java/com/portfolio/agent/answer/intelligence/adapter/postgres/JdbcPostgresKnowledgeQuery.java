package com.portfolio.agent.answer.intelligence.adapter.postgres;

import com.portfolio.agent.answer.gateway.LocalEmbeddingPort;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalRequest;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetrievalException;
import com.portfolio.agent.selection.adapter.postgres.JdbcPostgresSelectionQuery;
import com.portfolio.agent.selection.adapter.postgres.PostgresHybridCandidateRetriever;
import com.portfolio.agent.selection.adapter.postgres.PostgresSelectionQuery;
import com.portfolio.agent.selection.domain.CandidateRetrievalResult;
import com.portfolio.agent.selection.domain.SelectionTarget;
import com.portfolio.agent.selection.gateway.CandidateRetrievalException;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcPostgresKnowledgeQuery implements PostgresKnowledgeQuery {

    private static final String FIXED_AUDIENCE_ROLE = "PORTFOLIO_RETRIEVAL";
    private static final int FIXED_TARGET_SIZE = 3;

    private final PostgresHybridCandidateRetriever candidateRetriever;

    public JdbcPostgresKnowledgeQuery(JdbcTemplate jdbcTemplate, LocalEmbeddingPort embeddingPort) {
        this(new JdbcPostgresSelectionQuery(Objects.requireNonNull(jdbcTemplate, "jdbcTemplate")), embeddingPort);
    }

    public JdbcPostgresKnowledgeQuery(
            PostgresSelectionQuery selectionQuery,
            LocalEmbeddingPort embeddingPort) {
        this.candidateRetriever = new PostgresHybridCandidateRetriever(
                Objects.requireNonNull(selectionQuery, "selectionQuery"),
                Objects.requireNonNull(embeddingPort, "embeddingPort"));
    }

    @Override
    public CandidateRetrievalResult retrieve(PortfolioRetrievalRequest request) {
        Objects.requireNonNull(request, "request");
        SelectionTarget target = new SelectionTarget(
                request.getConditions().getCareerTrack(),
                FIXED_AUDIENCE_ROLE,
                request.getConditions().getCapabilityCodes(),
                request.getQuery(),
                FIXED_TARGET_SIZE);
        try {
            return candidateRetriever.retrieve(target, request.getLimit());
        } catch (CandidateRetrievalException exception) {
            throw new PortfolioRetrievalException("PostgreSQL public retrieval is unavailable", exception);
        }
    }
}
