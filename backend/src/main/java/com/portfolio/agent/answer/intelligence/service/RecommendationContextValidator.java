package com.portfolio.agent.answer.intelligence.service;

import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationContext;
import com.portfolio.agent.selection.domain.SelectionCandidate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RecommendationContextValidator {

    private final RecommendationBatchFingerprint fingerprint;

    public RecommendationContextValidator(RecommendationBatchFingerprint fingerprint) {
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
    }

    public RecommendationContextValidation validate(
            PortfolioRecommendationContext context,
            String currentContentVersion,
            PortfolioConditions currentConditions,
            List<SelectionCandidate> currentAllowedCandidates) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(currentConditions, "currentConditions");
        Objects.requireNonNull(currentAllowedCandidates, "currentAllowedCandidates");
        if (!context.getContentVersion().equals(requireText(currentContentVersion, "currentContentVersion"))) {
            return RecommendationContextValidation.invalid(
                    RecommendationContextValidationFailureCode.CONTENT_VERSION_MISMATCH);
        }
        if (context.getRequestedSize() != currentConditions.getRequestedSize()) {
            return RecommendationContextValidation.invalid(
                    RecommendationContextValidationFailureCode.REQUESTED_SIZE_MISMATCH);
        }
        if (!sameControlledConditions(context, currentConditions)) {
            return RecommendationContextValidation.invalid(
                    RecommendationContextValidationFailureCode.CONDITIONS_MISMATCH);
        }
        if (hasDuplicates(context.getSelectedPortfolioIds())) {
            return RecommendationContextValidation.invalid(
                    RecommendationContextValidationFailureCode.DUPLICATE_SELECTED_PORTFOLIO_ID);
        }
        Map<String, SelectionCandidate> candidatesById = byId(currentAllowedCandidates);
        for (String selectedId : context.getSelectedPortfolioIds()) {
            SelectionCandidate selectedCandidate = candidatesById.get(selectedId);
            if (selectedCandidate == null) {
                return RecommendationContextValidation.invalid(
                        RecommendationContextValidationFailureCode.SELECTED_PORTFOLIO_ID_NOT_ALLOWED);
            }
            if (selectedCandidate.getEvidenceReferences().isEmpty()
                    || !selectedCandidate.getEvidenceReferences().stream()
                    .allMatch(reference -> reference.isApproved())) {
                return RecommendationContextValidation.invalid(
                        RecommendationContextValidationFailureCode.SELECTED_PORTFOLIO_EVIDENCE_NOT_APPROVED);
            }
        }
        String expectedFingerprint = fingerprint.calculate(
                currentContentVersion, currentConditions, context.getSelectedPortfolioIds());
        if (!expectedFingerprint.equals(context.getRecommendationBatchId())) {
            return RecommendationContextValidation.invalid(
                    RecommendationContextValidationFailureCode.BATCH_FINGERPRINT_MISMATCH);
        }
        return RecommendationContextValidation.valid();
    }

    private boolean sameControlledConditions(
            PortfolioRecommendationContext context,
            PortfolioConditions currentConditions) {
        return Objects.equals(context.getCareerTrack(), currentConditions.getCareerTrack())
                && context.getAudienceRole().equals(currentConditions.getAudienceRole())
                && context.getCapabilityCodes().equals(currentConditions.getCapabilityCodes());
    }

    private boolean hasDuplicates(List<String> selectedPortfolioIds) {
        return new HashSet<>(selectedPortfolioIds).size() != selectedPortfolioIds.size();
    }

    private Map<String, SelectionCandidate> byId(List<SelectionCandidate> currentAllowedCandidates) {
        Map<String, SelectionCandidate> byId = new HashMap<>();
        for (SelectionCandidate candidate : currentAllowedCandidates) {
            SelectionCandidate previous = byId.put(candidate.getSubjectId(), candidate);
            if (previous != null) {
                throw new IllegalArgumentException("currentAllowedCandidates contains duplicate subjectId");
            }
        }
        return byId;
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
