package com.portfolio.agent.answer.intelligence.adapter.postgres;

import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedPassage;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedSubject;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalRequest;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalSource;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetrievalException;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetriever;
import com.portfolio.agent.selection.domain.CandidateRetrievalResult;
import com.portfolio.agent.selection.domain.EvidenceReference;
import com.portfolio.agent.selection.domain.SelectionCandidate;
import com.portfolio.agent.selection.gateway.CandidateRetrievalException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataAccessException;

public final class PostgresPortfolioRetriever implements PortfolioRetriever {

    private static final PortfolioRetrievalSource SOURCE = new PortfolioRetrievalSource("POSTGRES_PGVECTOR");

    private final PostgresKnowledgeQuery knowledgeQuery;

    public PostgresPortfolioRetriever(PostgresKnowledgeQuery knowledgeQuery) {
        this.knowledgeQuery = Objects.requireNonNull(knowledgeQuery, "knowledgeQuery");
    }

    @Override
    public PortfolioRetrievalResult retrieve(PortfolioRetrievalRequest request) {
        Objects.requireNonNull(request, "request");
        CandidateRetrievalResult result;
        try {
            result = knowledgeQuery.retrieve(request);
        } catch (PortfolioRetrievalException exception) {
            throw exception;
        } catch (CandidateRetrievalException exception) {
            throw new PortfolioRetrievalException("PostgreSQL public retrieval is unavailable", exception);
        } catch (DataAccessException exception) {
            throw new PortfolioRetrievalException("PostgreSQL public retrieval is unavailable", exception);
        }
        List<SelectionCandidate> candidates = result.getCandidates().stream()
                .filter(candidate -> !candidate.getEvidenceReferences().isEmpty())
                .toList();
        List<PortfolioRetrievedSubject> subjects = candidates.stream()
                .map(this::toSubject)
                .toList();
        List<PortfolioRetrievedPassage> passages = candidates.stream()
                .flatMap(candidate -> candidate.getEvidenceReferences().stream()
                        .sorted(Comparator.comparing(EvidenceReference::getClaimId)
                                .thenComparing(EvidenceReference::getEvidenceId))
                        .map(reference -> toPassage(candidate, reference)))
                .toList();
        return new PortfolioRetrievalResult(
                result.getReleaseVersion(), subjects, passages, SOURCE, false, null);
    }

    private PortfolioRetrievedSubject toSubject(SelectionCandidate candidate) {
        return new PortfolioRetrievedSubject(
                candidate.getSubjectId(), candidate.getSubjectKind().name(), candidate.getTitle(),
                candidate.getSummary(), candidate.getRoute(), candidate.getCapabilityCodes());
    }

    private PortfolioRetrievedPassage toPassage(
            SelectionCandidate candidate,
            EvidenceReference reference) {
        return new PortfolioRetrievedPassage(
                candidate.getSubjectId() + "#" + reference.getClaimId() + "#" + reference.getEvidenceId(),
                candidate.getSubjectId(), reference.getClaimId(), candidate.getSummary(),
                List.of(reference.getEvidenceId()));
    }
}
