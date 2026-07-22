package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.ContextResolution;
import com.portfolio.agent.answer.domain.ContextResolutionType;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.domain.ValidatedContextEnvelope;
import com.portfolio.agent.answer.dto.request.ContextEnvelopeRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public final class ContextEnvelopeValidator {

    public ContextResolution validate(
            RuntimeAnswerContent content,
            ContextEnvelopeRequest request
    ) {
        List<AnswerKnowledge> projects = request.getProjectSlugs().stream()
                .map(slug -> content.getProjects().stream()
                        .filter(project -> project.getSlug().equals(slug))
                        .findFirst()
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
        if (projects.size() != request.getProjectSlugs().size()) {
            return ContextResolution.invalid();
        }
        List<AnswerClaimProjection> claims = projects.stream()
                .flatMap(project -> project.getClaims().stream())
                .filter(claim -> request.getReferencedClaimIds().contains(claim.getId()))
                .toList();
        if (claims.size() != request.getReferencedClaimIds().size()) {
            return ContextResolution.invalid();
        }
        if (!presetExists(projects, request.getQuestionPresetId())) {
            return ContextResolution.invalid();
        }
        if (!sectionCompatible(request.getSelectedSectionType(), claims)) {
            return ContextResolution.invalid();
        }
        ContextResolutionType type = content.getContentVersion()
                .equals(request.getPreviousContentVersion())
                ? ContextResolutionType.VALID
                : ContextResolutionType.VERSION_UPDATED;
        ValidatedContextEnvelope envelope = new ValidatedContextEnvelope(
                content.getContentVersion(),
                request.getProjectSlugs(),
                request.getQuestionPresetId(),
                request.getReferencedClaimIds(),
                request.getSelectedSectionType(),
                request.getFollowUpIntent());
        return ContextResolution.valid(type, envelope);
    }

    private boolean presetExists(List<AnswerKnowledge> projects, String questionPresetId) {
        if (questionPresetId == null) {
            return true;
        }
        return projects.stream()
                .flatMap(project -> project.getQuestions().stream())
                .anyMatch(question -> question.getId().equals(questionPresetId));
    }

    private boolean sectionCompatible(
            AnswerSectionType sectionType,
            List<AnswerClaimProjection> claims
    ) {
        if (sectionType == null || claims.isEmpty()) {
            return true;
        }
        Set<AnswerClaimCategory> categories = switch (sectionType) {
            case BACKGROUND -> Set.of(AnswerClaimCategory.BACKGROUND);
            case RESPONSIBILITY -> Set.of(AnswerClaimCategory.RESPONSIBILITY);
            case SOLUTION -> Set.of(
                    AnswerClaimCategory.TECHNICAL_DECISION,
                    AnswerClaimCategory.IMPLEMENTATION);
            case VERIFICATION -> Set.of(AnswerClaimCategory.VERIFICATION);
            case STATUS -> Set.of(
                    AnswerClaimCategory.OUTCOME,
                    AnswerClaimCategory.LIMITATION);
            case BOUNDARY, REJECTED -> Set.of();
        };
        return !categories.isEmpty()
                && claims.stream().allMatch(claim -> categories.contains(claim.getCategory()));
    }
}
