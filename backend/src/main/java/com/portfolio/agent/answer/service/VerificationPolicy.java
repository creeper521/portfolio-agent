package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.GeneratedAnswer;
import com.portfolio.agent.answer.domain.ResolvedAnswerContext;
import com.portfolio.agent.answer.domain.VerificationStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerClaimVerificationStatus;
import com.portfolio.agent.answer.domain.AnswerMateriality;
import com.portfolio.agent.answer.domain.AnswerVerificationBasis;

@Component
public final class VerificationPolicy {

    public VerificationStatus verify(
            AnswerResolution resolution,
            ResolvedAnswerContext context,
            GeneratedAnswer answer
    ) {
        if (resolution != AnswerResolution.ANSWERED) {
            return VerificationStatus.NOT_APPLICABLE;
        }
        Map<String, AnswerClaimProjection> claimsById = context.getProject().getClaims().stream()
                .collect(Collectors.toUnmodifiableMap(AnswerClaimProjection::getId, Function.identity()));
        List<AnswerClaimProjection> referenced = answer.getSections().stream()
                .flatMap(section -> section.getClaimIds().stream())
                .distinct()
                .map(claimsById::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        List<AnswerClaimProjection> keyClaims = referenced.stream()
                .filter(claim -> claim.getMateriality() == AnswerMateriality.KEY)
                .toList();
        if (keyClaims.isEmpty()) {
            return VerificationStatus.UNVERIFIED;
        }
        if (keyClaims.stream().anyMatch(claim -> claim.getVerificationBasis() == AnswerVerificationBasis.UNSUPPORTED
                || claim.getVerificationStatus() == AnswerClaimVerificationStatus.UNVERIFIED)) {
            return VerificationStatus.UNVERIFIED;
        }
        if (keyClaims.stream().anyMatch(claim -> claim.getVerificationBasis() != AnswerVerificationBasis.EVIDENCE_SUPPORTED)) {
            return VerificationStatus.PARTIALLY_VERIFIED;
        }
        boolean allDirectlySupported = keyClaims.stream().allMatch(claim ->
                claim.getVerificationStatus() == AnswerClaimVerificationStatus.VERIFIED
                        && !claim.getDirectEvidenceIds().isEmpty()
                        && answer.getSections().stream()
                        .filter(section -> section.getClaimIds().contains(claim.getId()))
                        .allMatch(section -> section.getEvidenceIds().containsAll(claim.getDirectEvidenceIds())));
        return allDirectlySupported ? VerificationStatus.VERIFIED : VerificationStatus.PARTIALLY_VERIFIED;
    }
}
