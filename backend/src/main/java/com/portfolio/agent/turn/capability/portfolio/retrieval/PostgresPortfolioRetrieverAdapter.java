package com.portfolio.agent.turn.capability.portfolio.retrieval;

import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.EvidenceReference;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.SelectionCandidate;
import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceInvocation;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.PostgresKnowledgePassageRow;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.PostgresKnowledgeQuery;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.PostgresKnowledgeQueryResult;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.PostgresRetrievalFailureClassifier;
import com.portfolio.agent.turn.execution.TurnDeadline;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** PostgreSQL 查询结果到最终候选模型的唯一适配器。 */
public final class PostgresPortfolioRetrieverAdapter implements PortfolioRetrieverPort {

    private final PostgresKnowledgeQuery knowledgeQuery;

    public PostgresPortfolioRetrieverAdapter(PostgresKnowledgeQuery knowledgeQuery) {
        this.knowledgeQuery = Objects.requireNonNull(knowledgeQuery, "knowledgeQuery");
    }

    @Override
    public RetrievalAttemptResult retrieve(
            PortfolioEvidenceInvocation invocation,
            RetrievalRequest request,
            TurnDeadline deadline) {
        Objects.requireNonNull(invocation, "invocation");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(deadline, "deadline");
        if (deadline.isExpired()) {
            return RetrievalAttemptResult.failure(RetrievalAttemptFailure.CANCELLED);
        }
        try {
            PostgresKnowledgeQueryResult result = knowledgeQuery.retrieve(invocation, request);
            if (deadline.isExpired()) {
                return RetrievalAttemptResult.failure(RetrievalAttemptFailure.CANCELLED);
            }
            if (!invocation.getContentReleaseId().equals(
                    result.getCandidates().getReleaseVersion())) {
                return RetrievalAttemptResult.failure(RetrievalAttemptFailure.INTEGRITY_FAILURE);
            }
            return RetrievalAttemptResult.success(candidateSet(invocation, result));
        } catch (RuntimeException failure) {
            return RetrievalAttemptResult.failure(
                    PostgresRetrievalFailureClassifier.classify(failure));
        }
    }

    private PortfolioCandidateSet candidateSet(
            PortfolioEvidenceInvocation invocation,
            PostgresKnowledgeQueryResult result) {
        Map<String, List<PostgresKnowledgePassageRow>> passagesBySubject = result.getPassages().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        PostgresKnowledgePassageRow::getSubjectId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        List<CandidateSubject> subjects = result.getCandidates().getCandidates().stream()
                .filter(candidate -> candidate.getEvidenceReferences().stream()
                        .anyMatch(EvidenceReference::isApproved))
                .filter(candidate -> passagesBySubject.containsKey(candidate.getSubjectId()))
                .map(candidate -> toSubject(
                        result.getCandidates().getReleaseVersion(), candidate,
                        passagesBySubject.get(candidate.getSubjectId())))
                .toList();
        return new PortfolioCandidateSet(
                result.getCandidates().getReleaseVersion(),
                invocation.getSubjectScope(),
                subjects);
    }

    private CandidateSubject toSubject(
            String contentVersion,
            SelectionCandidate subject,
            List<PostgresKnowledgePassageRow> passages) {
        List<ClaimEvidenceCandidate> candidates = new ArrayList<>();
        LinkedHashSet<String> identities = new LinkedHashSet<>();
        for (PostgresKnowledgePassageRow passage : passages) {
            for (EvidenceReference evidence : passage.getEvidenceReferences()) {
                String identity = passage.getClaimId() + "\u0000" + evidence.getEvidenceId();
                if (!evidence.isApproved() || !identities.add(identity)) {
                    continue;
                }
                PublicEvidenceDescriptor descriptor = new PublicEvidenceDescriptor(
                        evidence.getEvidenceId(), evidence.getEvidenceCode(), evidence.getLabel(),
                        contentVersion, evidence.getPublicStatus(),
                        PublicEvidenceDescriptor.SourceType.valueOf(evidence.getEvidenceType()),
                        subject.getRoute(), "/evidence?evidence=" + evidence.getEvidenceId(),
                        LocalDate.of(9999, 12, 31));
                candidates.add(new ClaimEvidenceCandidate(
                        subject.getSubjectId(), passage.getClaim(), descriptor,
                        passage.getClaimCategory().name()));
            }
        }
        return new CandidateSubject(
                subject.getSubjectId(), subject.getRoute(), subject.getTitle(),
                contentVersion, candidates);
    }
}
