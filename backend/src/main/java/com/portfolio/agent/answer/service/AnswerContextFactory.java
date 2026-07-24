package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerTurnSnapshot;
import com.portfolio.agent.answer.domain.QuestionResolution;
import com.portfolio.agent.answer.domain.RetrievalDecision;
import com.portfolio.agent.answer.domain.ResolvedAnswerContext;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.domain.ToolExecutionOutcome;
import com.portfolio.agent.answer.domain.ValidatedContextEnvelope;
import com.portfolio.agent.answer.dto.request.AnswerRequest;
import com.portfolio.agent.answer.exception.InvalidAnswerContextException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public final class AnswerContextFactory {

    public ResolvedAnswerContext create(
            AnswerTurnSnapshot turnSnapshot,
            QuestionResolution resolution,
            AnswerRequest request
    ) {
        List<AnswerEvidence> evidence = resolution.getResolution() == AnswerResolution.ANSWERED
                ? resolution.getProject().getEvidence()
                : List.of();
        Set<String> allowedEvidenceIds = resolution.getProject().getEvidence().stream()
                .map(AnswerEvidence::getId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!allowedEvidenceIds.containsAll(request.getContext().getFocusEvidenceIds())) {
            throw new InvalidAnswerContextException();
        }
        return new ResolvedAnswerContext(
                turnSnapshot,
                resolution.getProject(),
                resolution.getQuestionPreset(),
                evidence
        );
    }

    public ResolvedAnswerContext createRetrieval(
            RuntimeAnswerContent content,
            AnswerKnowledge project,
            RetrievalDecision decision,
            AnswerRequest request,
            String requestId
    ) {
        validateFocusEvidence(project, request);
        Set<String> selectedClaimIds = Set.copyOf(decision.getSelectedClaimIds());
        List<AnswerClaimProjection> selectedClaims = project.getClaims().stream()
                .filter(claim -> selectedClaimIds.contains(claim.getId()))
                .toList();
        if (selectedClaims.size() != selectedClaimIds.size()) {
            throw new InvalidAnswerContextException();
        }
        Set<String> selectedEvidenceIds = selectedClaims.stream()
                .flatMap(claim -> claim.getDirectEvidenceIds().stream())
                .collect(Collectors.toUnmodifiableSet());
        List<AnswerEvidence> selectedEvidence = project.getEvidence().stream()
                .filter(evidence -> selectedEvidenceIds.contains(evidence.getId()))
                .toList();
        if (selectedEvidence.size() != selectedEvidenceIds.size()) {
            throw new InvalidAnswerContextException();
        }
        AnswerTurnSnapshot turn = new AnswerTurnSnapshot(
                request.getTurnId(),
                requestId,
                content.getContentVersion(),
                content.getRuntimeBundleHash(),
                project.getSlug(),
                null,
                selectedEvidence.stream().map(AnswerEvidence::getId).toList(),
                request.getContext().getAudienceRole(),
                request.getContext().getSource());
        return ResolvedAnswerContext.forRetrieval(
                turn,
                project,
                canonicalIntent(project, selectedClaims),
                selectedClaims,
                selectedEvidence);
    }

    public ResolvedAnswerContext createTool(
            RuntimeAnswerContent content,
            ValidatedContextEnvelope envelope,
            ToolExecutionOutcome outcome,
            AnswerRequest request,
            String requestId
    ) {
        List<AnswerKnowledge> available = request.getContext().getCaseSlug() == null
                ? content.getProjects()
                : content.getCases();
        String requestedSlug = request.getContext().getCaseSlug() == null
                ? request.getContext().getProjectSlug()
                : request.getContext().getCaseSlug();
        AnswerKnowledge project = available.stream()
                .filter(candidate -> candidate.getSlug().equals(requestedSlug))
                .findFirst()
                .orElseThrow(InvalidAnswerContextException::new);
        List<AnswerClaimProjection> selectedClaims = outcome.getClaims();
        if (selectedClaims.isEmpty()) {
            throw new InvalidAnswerContextException();
        }
        List<AnswerEvidence> selectedEvidence = outcome.getEvidence();
        AnswerTurnSnapshot turn = new AnswerTurnSnapshot(
                request.getTurnId(),
                requestId,
                content.getContentVersion(),
                content.getRuntimeBundleHash(),
                project.getSubjectType()
                        == com.portfolio.agent.answer.domain.AnswerSubjectType.PROJECT
                        ? project.getSlug()
                        : null,
                project.getSubjectType()
                        == com.portfolio.agent.answer.domain.AnswerSubjectType.CASE
                        ? project.getSlug()
                        : null,
                envelope.getQuestionPresetId(),
                selectedEvidence.stream().map(AnswerEvidence::getId).distinct().toList(),
                request.getContext().getAudienceRole(),
                request.getContext().getSource());
        String canonicalIntent = "followUpIntent=" + envelope.getFollowUpIntent().name()
                + "; projects=" + String.join(",", envelope.getProjectSlugs())
                + "; cases=" + String.join(",", envelope.getCaseSlugs())
                + "; section=" + (envelope.getSelectedSectionType() == null
                        ? "none"
                        : envelope.getSelectedSectionType().name())
                + "; claims=" + String.join(",", envelope.getReferencedClaimIds());
        return ResolvedAnswerContext.forRetrieval(
                turn, project, canonicalIntent, selectedClaims, selectedEvidence);
    }

    private String canonicalIntent(
            AnswerKnowledge project,
            List<AnswerClaimProjection> selectedClaims
    ) {
        String categories = selectedClaims.stream()
                .map(claim -> claim.getCategory().name())
                .distinct()
                .sorted()
                .collect(Collectors.joining(","));
        String topics = selectedClaims.stream()
                .flatMap(claim -> claim.getTopics().stream())
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.joining(","));
        return "project=" + project.getSlug()
                + "; categories=" + categories
                + "; topics=" + topics;
    }

    private void validateFocusEvidence(AnswerKnowledge project, AnswerRequest request) {
        Set<String> allowedEvidenceIds = project.getEvidence().stream()
                .map(AnswerEvidence::getId)
                .collect(Collectors.toUnmodifiableSet());
        if (!allowedEvidenceIds.containsAll(request.getContext().getFocusEvidenceIds())) {
            throw new InvalidAnswerContextException();
        }
    }
}
