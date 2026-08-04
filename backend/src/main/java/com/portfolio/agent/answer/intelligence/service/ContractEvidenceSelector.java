package com.portfolio.agent.answer.intelligence.service;

import com.portfolio.agent.answer.intelligence.domain.PortfolioContractTask;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedEvidenceReference;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedPassage;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalRequest;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetriever;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Selects the fixed evidence set promised by an active preset contract. */
public final class ContractEvidenceSelector {

    public static final String UNAVAILABLE_NOTICE = "PRESET_CONTRACT_UNAVAILABLE";

    private final PortfolioRetriever retriever;

    public ContractEvidenceSelector(PortfolioRetriever retriever) {
        this.retriever = Objects.requireNonNull(retriever, "retriever");
    }

    public PortfolioRetrievalResult select(PortfolioContractTask task) {
        Objects.requireNonNull(task, "task");
        Set<String> claimIds = new LinkedHashSet<>(task.getRequiredClaimIds());
        claimIds.addAll(task.getSupportingClaimIds());
        PortfolioRetrievalResult result = retriever.retrieve(PortfolioRetrievalRequest.contractScope(
                task.getCanonicalQuestion(), task.getSubjectId(), List.copyOf(claimIds)));
        return hasRequiredEvidence(task, result.getPassages())
                ? result
                : new PortfolioRetrievalResult(
                        result.getContentVersion(), result.getSubjects(), List.of(), result.getSource(),
                        result.isDegraded(), UNAVAILABLE_NOTICE);
    }

    private boolean hasRequiredEvidence(
            PortfolioContractTask task,
            List<PortfolioRetrievedPassage> passages
    ) {
        for (String requiredClaimId : task.getRequiredClaimIds()) {
            Set<String> approvedEvidenceIds = new LinkedHashSet<>();
            for (PortfolioRetrievedPassage passage : passages) {
                if (!requiredClaimId.equals(passage.getClaimId())) {
                    continue;
                }
                for (PortfolioRetrievedEvidenceReference reference : passage.getEvidenceReferences()) {
                    if (reference.isApproved()) {
                        approvedEvidenceIds.add(reference.getEvidenceId());
                    }
                }
            }
            if (approvedEvidenceIds.size() < task.getMinimumApprovedEvidencePerRequiredClaim()) {
                return false;
            }
        }
        return true;
    }
}
