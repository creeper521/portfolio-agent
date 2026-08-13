package com.portfolio.agent.answer.intelligence.execution.adapter.bundle;

import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalRequest;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievalResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedEvidenceReference;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedPassage;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedSubject;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import com.portfolio.agent.answer.intelligence.execution.capability.CapabilityExecutionResult;
import com.portfolio.agent.answer.intelligence.execution.capability.PortfolioCandidateRetrievalPort;
import com.portfolio.agent.answer.intelligence.execution.domain.AuthorizedSubjectScope;
import com.portfolio.agent.answer.intelligence.execution.domain.CandidateCoverageReport;
import com.portfolio.agent.answer.intelligence.execution.domain.CandidateSubject;
import com.portfolio.agent.answer.intelligence.execution.domain.CapabilityExecutionConstraints;
import com.portfolio.agent.answer.intelligence.execution.domain.ClaimEvidenceCandidate;
import com.portfolio.agent.answer.intelligence.execution.domain.PortfolioEvidenceInvocation;
import com.portfolio.agent.answer.intelligence.execution.domain.PortfolioRetrievalCandidateSet;
import com.portfolio.agent.answer.intelligence.execution.domain.PublicEvidenceDescriptor;
import com.portfolio.agent.answer.intelligence.execution.domain.SafeReasonCode;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetrievalException;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetrievalFailureKind;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetriever;
import com.portfolio.agent.answer.routing.domain.SubjectReference;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Adapts the existing bundle retriever into the P3 candidate boundary. */
public final class BundlePortfolioCandidateRetrievalAdapter implements PortfolioCandidateRetrievalPort {
    private static final String FIXED_PROFILE_QUERY = "p3-portfolio-profile-v1";
    private final PortfolioRetriever retriever;

    public BundlePortfolioCandidateRetrievalAdapter(PortfolioRetriever retriever) {
        this.retriever = Objects.requireNonNull(retriever, "retriever");
    }

    @Override
    public CapabilityExecutionResult retrieve(
            PortfolioEvidenceInvocation invocation,
            CapabilityExecutionConstraints constraints) {
        Objects.requireNonNull(invocation, "invocation");
        Objects.requireNonNull(constraints, "constraints");
        try {
            PortfolioRetrievalResult result = retriever.retrieve(toRequest(invocation));
            PortfolioRetrievalCandidateSet candidateSet = toCandidateSet(invocation, result);
            com.portfolio.agent.answer.intelligence.execution.validation.ValidatedEvidenceBundle bundle =
                    new com.portfolio.agent.answer.intelligence.execution.validation.EvidencePromotionValidator()
                            .promote(candidateSet, invocation.getExpectedContentVersion());
            return candidateSet.getEvidenceUnitCount() == 0
                    ? CapabilityExecutionResult.empty(candidateSet, bundle)
                    : CapabilityExecutionResult.success(candidateSet, bundle);
        } catch (PortfolioRetrievalException exception) {
            if (exception.getKind() == PortfolioRetrievalFailureKind.TIMEOUT) {
                return CapabilityExecutionResult.timedOut();
            }
            if (exception.getKind() == PortfolioRetrievalFailureKind.CONNECTION_UNAVAILABLE) {
                return CapabilityExecutionResult.unavailable(
                        SafeReasonCode.CAPABILITY_TEMPORARILY_UNAVAILABLE);
            }
            return CapabilityExecutionResult.integrityFailed();
        } catch (IllegalArgumentException exception) {
            return CapabilityExecutionResult.integrityFailed();
        }
    }

    private PortfolioRetrievalRequest toRequest(PortfolioEvidenceInvocation invocation) {
        List<AnswerClaimCategory> categories = categories(invocation);
        AuthorizedSubjectScope scope = invocation.getAuthorizedSubjectScope();
        if (scope.getMode() == AuthorizedSubjectScope.ScopeMode.EXACT_SUBJECTS) {
            return PortfolioRetrievalRequest.referenceScope(
                    FIXED_PROFILE_QUERY, PortfolioTaskMode.FACT_LOOKUP, PortfolioConditions.empty(),
                    scope.getExactSubjects().stream().map(SubjectReference::getSubjectId).toList(),
                    List.of(), categories);
        }
        return new PortfolioRetrievalRequest(
                FIXED_PROFILE_QUERY, PortfolioTaskMode.RECOMMENDATION,
                PortfolioConditions.empty(), PortfolioRetrievalCandidateSet.MAX_SUBJECTS);
    }

    private PortfolioRetrievalCandidateSet toCandidateSet(
            PortfolioEvidenceInvocation invocation, PortfolioRetrievalResult result) {
        if (!invocation.getExpectedContentVersion().equals(result.getContentVersion())) {
            throw new IllegalArgumentException("content version mismatch");
        }
        Map<String, List<PortfolioRetrievedPassage>> passagesBySubject = new LinkedHashMap<>();
        for (PortfolioRetrievedPassage passage : result.getPassages()) {
            passagesBySubject.computeIfAbsent(passage.getSubjectId(), key -> new ArrayList<>()).add(passage);
        }
        List<CandidateSubject> subjects = new ArrayList<>();
        for (PortfolioRetrievedSubject subject : result.getSubjects()) {
            List<ClaimEvidenceCandidate> candidates = new ArrayList<>();
            for (PortfolioRetrievedPassage passage : passagesBySubject.getOrDefault(
                    subject.getSubjectId(), List.of())) {
                for (PortfolioRetrievedEvidenceReference evidence : passage.getEvidenceReferences()) {
                    PublicEvidenceDescriptor descriptor = new PublicEvidenceDescriptor(
                            evidence.getEvidenceId(), evidence.getEvidenceCode(), evidence.getLabel(),
                            result.getContentVersion(), evidence.getPublicStatus(),
                            PublicEvidenceDescriptor.SourceType.valueOf(evidence.getEvidenceType()),
                            subject.getRoute(),
                            "/evidence?evidence=" + evidence.getEvidenceId(), LocalDate.of(9999, 12, 31));
                    candidates.add(new ClaimEvidenceCandidate(subject.getSubjectId(), passage.getClaim(),
                            descriptor, profileTarget(invocation)));
                }
            }
            subjects.add(new CandidateSubject(subject.getSubjectId(), subject.getRoute(), subject.getTitle(),
                    result.getContentVersion(), candidates));
        }
        Map<String, CandidateCoverageReport.CoverageStatus> coverage = new LinkedHashMap<>();
        List<String> targets = targetLabels(invocation);
        List<String> subjectIds = invocation.getAuthorizedSubjectScope().getMode()
                == AuthorizedSubjectScope.ScopeMode.EXACT_SUBJECTS
                ? invocation.getAuthorizedSubjectScope().getExactSubjects().stream()
                .map(SubjectReference::getSubjectId).toList()
                : subjects.stream().map(CandidateSubject::getSubjectId).toList();
        for (String subjectId : subjectIds) {
            for (String target : targets) {
                boolean matched = subjects.stream().filter(value -> subjectId.equals(value.getSubjectId()))
                        .flatMap(value -> value.getCandidates().stream())
                        .anyMatch(candidate -> target.equals(candidate.getRetrievalTarget()));
                coverage.put(subjectId + "/" + target,
                        matched ? CandidateCoverageReport.CoverageStatus.MATCHED
                                : CandidateCoverageReport.CoverageStatus.EVALUATED_NO_QUALIFYING_MATCH);
            }
        }
        if (coverage.isEmpty()) coverage.put("scope", CandidateCoverageReport.CoverageStatus.EVALUATED_NO_QUALIFYING_MATCH);
        return new PortfolioRetrievalCandidateSet(
                "PORTFOLIO_EVIDENCE_RETRIEVAL_V1", 1, result.getContentVersion(),
                invocation.getAuthorizedSubjectScope(), subjects,
                new CandidateCoverageReport(coverage));
    }

    private List<AnswerClaimCategory> categories(PortfolioEvidenceInvocation invocation) {
        LinkedHashSet<AnswerClaimCategory> categories = new LinkedHashSet<>();
        invocation.getFacetProfiles().forEach(profile -> profile.getClaimCategories().forEach(
                value -> categories.add(AnswerClaimCategory.valueOf(value))));
        invocation.getComparisonDimensionProfiles().forEach(profile -> profile.getClaimCategories().forEach(
                value -> categories.add(AnswerClaimCategory.valueOf(value))));
        return List.copyOf(categories);
    }

    private List<String> targetLabels(PortfolioEvidenceInvocation invocation) {
        List<String> targets = new ArrayList<>();
        invocation.getFacetProfiles().forEach(profile -> targets.add(profile.getFacet().name()));
        invocation.getComparisonDimensionProfiles().forEach(profile -> targets.add(profile.getDimension().name()));
        return List.copyOf(targets);
    }

    private String profileTarget(PortfolioEvidenceInvocation invocation) {
        if (!invocation.getFacetProfiles().isEmpty()) return invocation.getFacetProfiles().get(0).getFacet().name();
        return invocation.getComparisonDimensionProfiles().get(0).getDimension().name();
    }
}
