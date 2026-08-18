package com.portfolio.agent.turn.capability.portfolio.retrieval;

import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalRequest;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedPassage;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetrievalException;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetrievalFailureKind;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetriever;
import com.portfolio.agent.turn.capability.portfolio.PortfolioEvidenceInvocation;
import com.portfolio.agent.turn.execution.TurnDeadline;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

final class PortfolioRetrieverAdapterSupport {
    private final PortfolioRetriever retriever;
    PortfolioRetrieverAdapterSupport(PortfolioRetriever retriever) {
        this.retriever = java.util.Objects.requireNonNull(retriever, "retriever");
    }

    RetrievalAttemptResult retrieve(
            PortfolioEvidenceInvocation invocation,
            RetrievalRequest request,
            TurnDeadline deadline) {
        if (deadline.isExpired()) {
            return RetrievalAttemptResult.failure(RetrievalAttemptFailure.CANCELLED);
        }
        try {
            PortfolioRetrievalResult result = retriever.retrieve(toRequest(invocation, request));
            if (deadline.isExpired()) {
                return RetrievalAttemptResult.failure(RetrievalAttemptFailure.CANCELLED);
            }
            return RetrievalAttemptResult.success(toCandidateSet(invocation, result));
        } catch (PortfolioRetrievalException failure) {
            return RetrievalAttemptResult.failure(switch (failure.getKind()) {
                case TIMEOUT -> RetrievalAttemptFailure.BACKEND_TIMEOUT;
                case CONNECTION_UNAVAILABLE -> RetrievalAttemptFailure.BACKEND_CONNECTION_UNAVAILABLE;
                default -> RetrievalAttemptFailure.INTEGRITY_FAILURE;
            });
        } catch (IllegalArgumentException failure) {
            return RetrievalAttemptResult.failure(RetrievalAttemptFailure.INTEGRITY_FAILURE);
        }
    }

    private PortfolioRetrievalRequest toRequest(
            PortfolioEvidenceInvocation invocation, RetrievalRequest request) {
        List<AnswerClaimCategory> categories = categories(invocation);
        if (invocation.getSubjectScope().getMode()
                == com.portfolio.agent.turn.capability.portfolio.AuthorizedSubjectScope.Mode.EXACT) {
            return PortfolioRetrievalRequest.referenceScope(
                    "portfolio-profile-v1", mode(invocation), PortfolioConditions.empty(),
                    invocation.getSubjectScope().getSubjects().stream()
                            .map(value -> value.getReference()).toList(),
                    List.of(), categories).withSearchStrategy(request.getStrategy());
        }
        return PortfolioRetrievalRequest.profileDiscovery(
                "portfolio-profile-v1", PortfolioConditions.empty(),
                PortfolioRetrievalRequest.MAX_LIMIT, categories)
                .withSearchStrategy(request.getStrategy());
    }

    private PortfolioTaskMode mode(PortfolioEvidenceInvocation invocation) {
        return switch (invocation.getTaskType()) {
            case PORTFOLIO_FACT -> PortfolioTaskMode.FACT_LOOKUP;
            case PORTFOLIO_COMPARE -> PortfolioTaskMode.COMPARISON;
            case PORTFOLIO_RECOMMEND -> PortfolioTaskMode.RECOMMENDATION;
            case PORTFOLIO_REFINE_RECOMMENDATION -> PortfolioTaskMode.REFINE_RECOMMENDATION;
            default -> throw new IllegalArgumentException("unsupported portfolio task");
        };
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
            case RECOMMENDATION -> List.of(AnswerClaimCategory.BACKGROUND,
                    AnswerClaimCategory.IMPLEMENTATION, AnswerClaimCategory.VERIFICATION,
                    AnswerClaimCategory.OUTCOME, AnswerClaimCategory.TECHNICAL_DECISION);
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

    private PortfolioCandidateSet toCandidateSet(
            PortfolioEvidenceInvocation invocation, PortfolioRetrievalResult result) {
        if (!invocation.getContentReleaseId().equals(result.getContentVersion())) {
            throw new IllegalArgumentException("content release mismatch");
        }
        Map<String, List<PortfolioRetrievedPassage>> passages = new LinkedHashMap<>();
        result.getPassages().forEach(passage -> passages
                .computeIfAbsent(passage.getSubjectId(), ignored -> new ArrayList<>()).add(passage));
        List<CandidateSubject> subjects = result.getSubjects().stream().map(subject -> {
            List<ClaimEvidenceCandidate> candidates = new ArrayList<>();
            LinkedHashSet<String> identities = new LinkedHashSet<>();
            passages.getOrDefault(subject.getSubjectId(), List.of()).forEach(passage ->
                    passage.getEvidenceReferences().forEach(evidence -> {
                        String identity = passage.getClaim().getId() + "\u0000" + evidence.getEvidenceId();
                        if (!identities.add(identity)) return;
                        PublicEvidenceDescriptor descriptor = new PublicEvidenceDescriptor(
                                evidence.getEvidenceId(), evidence.getEvidenceCode(), evidence.getLabel(),
                                result.getContentVersion(), evidence.getPublicStatus(),
                                PublicEvidenceDescriptor.SourceType.valueOf(evidence.getEvidenceType()),
                                subject.getRoute(), "/evidence?evidence=" + evidence.getEvidenceId(),
                                LocalDate.of(9999, 12, 31));
                        candidates.add(new ClaimEvidenceCandidate(
                                subject.getSubjectId(), passage.getClaim(), descriptor,
                                passage.getClaim().getCategory().name()));
                    }));
            return new CandidateSubject(
                    subject.getSubjectId(), subject.getRoute(), subject.getTitle(),
                    result.getContentVersion(), candidates);
        }).toList();
        return new PortfolioCandidateSet(
                result.getContentVersion(), invocation.getSubjectScope(), subjects);
    }
}
