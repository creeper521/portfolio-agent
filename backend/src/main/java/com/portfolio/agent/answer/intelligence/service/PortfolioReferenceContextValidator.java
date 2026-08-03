package com.portfolio.agent.answer.intelligence.service;

import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerSubjectType;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.intelligence.domain.PortfolioReferenceContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PortfolioReferenceContextValidator {

    public PortfolioReferenceResolution validate(
            RuntimeAnswerContent content,
            PortfolioReferenceContext reference
    ) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(reference, "reference");
        if (!shapeIsValid(reference)) {
            return PortfolioReferenceResolution.invalid();
        }
        List<AnswerKnowledge> selected = selectedSubjects(content, reference);
        int requestedSubjectCount = reference.getProjectSlugs().size()
                + reference.getCaseSlugs().size();
        if (selected.isEmpty()) {
            return PortfolioReferenceResolution.referencesMissing();
        }
        if (selected.size() != requestedSubjectCount) {
            return PortfolioReferenceResolution.invalid();
        }
        if (!presetExists(selected, reference.getQuestionPresetId())) {
            return PortfolioReferenceResolution.invalid();
        }
        Set<String> availableClaimIds = new HashSet<>();
        for (AnswerKnowledge subject : selected) {
            for (AnswerClaimProjection claim : subject.getClaims()) {
                availableClaimIds.add(claim.getId());
            }
        }
        if (!availableClaimIds.containsAll(reference.getReferencedClaimIds())) {
            return PortfolioReferenceResolution.invalid();
        }
        List<String> subjectIds = selected.stream()
                .map(AnswerKnowledge::getStableId)
                .toList();
        PortfolioReferenceResolutionType type = Objects.equals(
                content.getContentVersion(), reference.getPreviousContentVersion())
                ? PortfolioReferenceResolutionType.VALID
                : PortfolioReferenceResolutionType.VERSION_UPDATED;
        return PortfolioReferenceResolution.resolved(
                type, subjectIds, reference.getReferencedClaimIds());
    }

    private boolean shapeIsValid(PortfolioReferenceContext reference) {
        boolean projectSelected = !reference.getProjectSlugs().isEmpty();
        boolean caseSelected = !reference.getCaseSlugs().isEmpty();
        return projectSelected != caseSelected
                && distinct(reference.getProjectSlugs())
                && distinct(reference.getCaseSlugs())
                && distinct(reference.getReferencedClaimIds());
    }

    private boolean distinct(List<String> values) {
        return new HashSet<>(values).size() == values.size();
    }

    private List<AnswerKnowledge> selectedSubjects(
            RuntimeAnswerContent content,
            PortfolioReferenceContext reference
    ) {
        List<AnswerKnowledge> selected = new ArrayList<>();
        for (AnswerKnowledge project : content.getProjects()) {
            if (project.getSubjectType() == AnswerSubjectType.PROJECT
                    && reference.getProjectSlugs().contains(project.getSlug())) {
                selected.add(project);
            }
        }
        for (AnswerKnowledge caseItem : content.getCases()) {
            if (caseItem.getSubjectType() == AnswerSubjectType.CASE
                    && reference.getCaseSlugs().contains(caseItem.getSlug())) {
                selected.add(caseItem);
            }
        }
        return List.copyOf(selected);
    }

    private boolean presetExists(
            List<AnswerKnowledge> selected,
            String questionPresetId
    ) {
        if (questionPresetId == null) {
            return true;
        }
        return selected.stream()
                .flatMap(subject -> subject.getQuestions().stream())
                .anyMatch(question -> questionPresetId.equals(question.getId()));
    }
}
