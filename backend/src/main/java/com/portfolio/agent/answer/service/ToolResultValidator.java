package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerQuestion;
import com.portfolio.agent.answer.domain.AnswerTimelineEvent;
import com.portfolio.agent.answer.domain.ExecutionBudgets;
import com.portfolio.agent.answer.domain.PublicToolResult;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.domain.ToolCall;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public final class ToolResultValidator {

    public void validate(
            RuntimeAnswerContent content,
            ToolCall call,
            PublicToolResult result,
            ExecutionBudgets budgets
    ) {
        if (!content.getContentVersion().equals(result.getContentVersion())
                || !content.getRuntimeBundleHash().equals(result.getRuntimeBundleHash())) {
            throw new IllegalArgumentException("tool result snapshot does not match");
        }
        if (call.getKind() != result.getKind()) {
            throw new IllegalArgumentException("tool kind does not match");
        }
        Set<String> allowedProjectSlugs = Set.copyOf(call.getProjectSlugs());
        Set<String> allowedCaseSlugs = Set.copyOf(call.getCaseSlugs());
        Set<String> resultProjectSlugs = result.getProjects().stream()
                .map(AnswerKnowledge::getSlug)
                .collect(Collectors.toUnmodifiableSet());
        if (!allowedProjectSlugs.containsAll(resultProjectSlugs)) {
            throw new IllegalArgumentException("tool result contains unauthorized project");
        }
        Set<String> publicProjectSlugs = content.getProjects().stream()
                .map(AnswerKnowledge::getSlug)
                .collect(Collectors.toUnmodifiableSet());
        if (!publicProjectSlugs.containsAll(resultProjectSlugs)) {
            throw new IllegalArgumentException("tool result contains unknown project");
        }
        Set<String> resultCaseSlugs = result.getCases().stream()
                .map(AnswerKnowledge::getSlug)
                .collect(Collectors.toUnmodifiableSet());
        if (!allowedCaseSlugs.containsAll(resultCaseSlugs)) {
            throw new IllegalArgumentException("tool result contains unauthorized case");
        }
        Set<String> publicCaseSlugs = content.getCases().stream()
                .map(AnswerKnowledge::getSlug)
                .collect(Collectors.toUnmodifiableSet());
        if (!publicCaseSlugs.containsAll(resultCaseSlugs)) {
            throw new IllegalArgumentException("tool result contains unknown case");
        }
        Set<String> publicClaimIds = allowedSubjects(
                content, allowedProjectSlugs, allowedCaseSlugs)
                .flatMap(project -> project.getClaims().stream())
                .map(AnswerClaimProjection::getId)
                .collect(Collectors.toUnmodifiableSet());
        Set<String> resultClaimIds = result.getClaims().stream()
                .map(AnswerClaimProjection::getId)
                .collect(Collectors.toUnmodifiableSet());
        if (!publicClaimIds.containsAll(resultClaimIds)) {
            throw new IllegalArgumentException("tool result contains unknown claim");
        }
        if (!call.getClaimIds().isEmpty()
                && !Set.copyOf(call.getClaimIds()).containsAll(resultClaimIds)) {
            throw new IllegalArgumentException("tool result contains unauthorized claim");
        }
        if (result.getClaims().size() > budgets.getMaxRetrievedClaims()) {
            throw new IllegalArgumentException("tool result exceeds claim budget");
        }
        validateEvidence(content, allowedProjectSlugs, allowedCaseSlugs, result);
        validateTimeline(content, result);
        validateQuestions(content, allowedProjectSlugs, allowedCaseSlugs, result);
        if (characterCount(result) > budgets.getMaxContextCharacters()) {
            throw new IllegalArgumentException("tool result exceeds context budget");
        }
    }

    private void validateEvidence(
            RuntimeAnswerContent content,
            Set<String> projectSlugs,
            Set<String> caseSlugs,
            PublicToolResult result
    ) {
        Set<String> publicEvidenceIds = allowedSubjects(content, projectSlugs, caseSlugs)
                .flatMap(project -> project.getEvidence().stream())
                .filter(evidence -> "APPROVED".equals(evidence.getPublicStatus()))
                .filter(evidence -> !evidence.isRawContentPublic())
                .map(AnswerEvidence::getId)
                .collect(Collectors.toUnmodifiableSet());
        if (!publicEvidenceIds.containsAll(result.getEvidence().stream()
                .map(AnswerEvidence::getId).toList())) {
            throw new IllegalArgumentException("tool result contains unknown evidence");
        }
    }

    private void validateTimeline(RuntimeAnswerContent content, PublicToolResult result) {
        Set<String> publicTimelineIds = content.getTimeline().stream()
                .map(AnswerTimelineEvent::getId)
                .collect(Collectors.toUnmodifiableSet());
        if (!publicTimelineIds.containsAll(result.getTimeline().stream()
                .map(AnswerTimelineEvent::getId).toList())) {
            throw new IllegalArgumentException("tool result contains unknown timeline event");
        }
    }

    private void validateQuestions(
            RuntimeAnswerContent content,
            Set<String> projectSlugs,
            Set<String> caseSlugs,
            PublicToolResult result
    ) {
        Set<String> publicQuestionIds = allowedSubjects(content, projectSlugs, caseSlugs)
                .flatMap(project -> project.getQuestions().stream())
                .map(AnswerQuestion::getId)
                .collect(Collectors.toUnmodifiableSet());
        if (!publicQuestionIds.containsAll(result.getQuestions().stream()
                .map(AnswerQuestion::getId).toList())) {
            throw new IllegalArgumentException("tool result contains unknown question");
        }
    }

    private java.util.stream.Stream<AnswerKnowledge> allowedSubjects(
            RuntimeAnswerContent content,
            Set<String> projectSlugs,
            Set<String> caseSlugs
    ) {
        return java.util.stream.Stream.concat(
                content.getProjects().stream()
                        .filter(project -> projectSlugs.contains(project.getSlug())),
                content.getCases().stream()
                        .filter(caseStudy -> caseSlugs.contains(caseStudy.getSlug())));
    }

    private int characterCount(PublicToolResult result) {
        int characters = 0;
        for (AnswerClaimProjection claim : result.getClaims()) {
            characters += claim.getStatement().length() + claim.getDetail().length();
        }
        for (AnswerEvidence evidence : result.getEvidence()) {
            characters += evidence.getTitle().length() + evidence.getSummary().length();
        }
        for (AnswerTimelineEvent event : result.getTimeline()) {
            characters += event.getTitle().length() + event.getProblem().length()
                    + event.getAction().length() + event.getImpact().length();
        }
        return characters;
    }
}
