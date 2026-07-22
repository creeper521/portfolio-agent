package com.portfolio.agent.answer.adapter.tool;

import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerClaimProjection;
import com.portfolio.agent.answer.domain.AnswerEvidence;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerQuestion;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.AnswerTimelineEvent;
import com.portfolio.agent.answer.domain.PublicToolResult;
import com.portfolio.agent.answer.domain.PublicToolResultStatus;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.domain.ToolCall;
import com.portfolio.agent.answer.gateway.PublicKnowledgeTools;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public final class LocalPublicKnowledgeTools implements PublicKnowledgeTools {

    @Override
    public PublicToolResult execute(RuntimeAnswerContent content, ToolCall call) {
        List<AnswerKnowledge> projects = selectedProjects(content, call.getProjectSlugs());
        if (projects.size() != call.getProjectSlugs().size()) {
            return result(content, call, PublicToolResultStatus.INSUFFICIENT,
                    List.of(), List.of(), List.of(), List.of(), List.of());
        }
        return switch (call.getKind()) {
            case GET_PROJECT -> result(content, call, PublicToolResultStatus.SUCCESS,
                    projects, List.of(), List.of(), List.of(), List.of());
            case GET_CLAIMS -> claimsResult(content, call, projects);
            case GET_EVIDENCE_FOR_CLAIMS -> evidenceResult(content, call, projects);
            case GET_TIMELINE -> timelineResult(content, call, projects);
            case SEARCH_PUBLIC_CONTENT -> searchResult(content, call, projects);
            case COMPARE_PROJECTS -> compareResult(content, call, projects);
        };
    }

    private PublicToolResult claimsResult(
            RuntimeAnswerContent content,
            ToolCall call,
            List<AnswerKnowledge> projects
    ) {
        List<AnswerClaimProjection> claims = selectedClaims(projects, call);
        PublicToolResultStatus status = claims.isEmpty()
                ? PublicToolResultStatus.INSUFFICIENT
                : PublicToolResultStatus.SUCCESS;
        return result(content, call, status, projects, claims,
                List.of(), List.of(), List.of());
    }

    private PublicToolResult evidenceResult(
            RuntimeAnswerContent content,
            ToolCall call,
            List<AnswerKnowledge> projects
    ) {
        List<AnswerClaimProjection> claims = selectedClaims(projects, call);
        Set<String> evidenceIds = claims.stream()
                .flatMap(claim -> claim.getDirectEvidenceIds().stream())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<AnswerEvidence> evidence = projects.stream()
                .flatMap(project -> project.getEvidence().stream())
                .filter(item -> evidenceIds.contains(item.getId()))
                .distinct()
                .toList();
        boolean claimWithoutEvidence = claims.stream()
                .anyMatch(claim -> claim.getDirectEvidenceIds().isEmpty());
        PublicToolResultStatus status = claims.isEmpty()
                || claimWithoutEvidence
                || evidenceIds.isEmpty()
                || evidence.size() != evidenceIds.size()
                ? PublicToolResultStatus.INSUFFICIENT
                : PublicToolResultStatus.SUCCESS;
        return result(content, call, status, projects, claims,
                evidence, List.of(), List.of());
    }

    private PublicToolResult timelineResult(
            RuntimeAnswerContent content,
            ToolCall call,
            List<AnswerKnowledge> projects
    ) {
        Set<String> projectSlugs = Set.copyOf(call.getProjectSlugs());
        Set<String> claimIds = Set.copyOf(call.getClaimIds());
        List<AnswerTimelineEvent> timeline = content.getTimeline().stream()
                .filter(event -> event.getProjectSlugs().stream().anyMatch(projectSlugs::contains))
                .filter(event -> claimIds.isEmpty()
                        || event.getClaimIds().stream().anyMatch(claimIds::contains))
                .toList();
        PublicToolResultStatus status = timeline.isEmpty()
                ? PublicToolResultStatus.INSUFFICIENT
                : PublicToolResultStatus.SUCCESS;
        return result(content, call, status, projects, List.of(),
                List.of(), timeline, List.of());
    }

    private PublicToolResult searchResult(
            RuntimeAnswerContent content,
            ToolCall call,
            List<AnswerKnowledge> projects
    ) {
        List<AnswerQuestion> questions = projects.stream()
                .flatMap(project -> project.getQuestions().stream())
                .toList();
        List<AnswerClaimProjection> claims = selectedClaims(projects, call);
        PublicToolResultStatus status = questions.isEmpty() && claims.isEmpty()
                ? PublicToolResultStatus.INSUFFICIENT
                : PublicToolResultStatus.SUCCESS;
        return result(content, call, status, projects, claims,
                List.of(), List.of(), questions);
    }

    private PublicToolResult compareResult(
            RuntimeAnswerContent content,
            ToolCall call,
            List<AnswerKnowledge> projects
    ) {
        if (projects.size() < 2) {
            return result(content, call, PublicToolResultStatus.INSUFFICIENT,
                    List.of(), List.of(), List.of(), List.of(), List.of());
        }
        List<AnswerClaimProjection> claims = projects.stream()
                .flatMap(project -> project.getClaims().stream())
                .toList();
        return result(content, call, PublicToolResultStatus.SUCCESS,
                projects, claims, List.of(), List.of(), List.of());
    }

    private List<AnswerKnowledge> selectedProjects(
            RuntimeAnswerContent content,
            List<String> projectSlugs
    ) {
        return projectSlugs.stream()
                .map(slug -> content.getProjects().stream()
                        .filter(project -> project.getSlug().equals(slug))
                        .findFirst()
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private List<AnswerClaimProjection> selectedClaims(
            List<AnswerKnowledge> projects,
            ToolCall call
    ) {
        Set<String> requestedClaimIds = Set.copyOf(call.getClaimIds());
        Set<AnswerClaimCategory> categories = categoriesFor(call.getSectionType());
        return projects.stream()
                .flatMap(project -> project.getClaims().stream())
                .filter(claim -> requestedClaimIds.isEmpty()
                        || requestedClaimIds.contains(claim.getId()))
                .filter(claim -> categories.isEmpty() || categories.contains(claim.getCategory()))
                .distinct()
                .toList();
    }

    private Set<AnswerClaimCategory> categoriesFor(AnswerSectionType sectionType) {
        if (sectionType == null) {
            return Set.of();
        }
        return switch (sectionType) {
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
    }

    private PublicToolResult result(
            RuntimeAnswerContent content,
            ToolCall call,
            PublicToolResultStatus status,
            List<AnswerKnowledge> projects,
            List<AnswerClaimProjection> claims,
            List<AnswerEvidence> evidence,
            List<AnswerTimelineEvent> timeline,
            List<AnswerQuestion> questions
    ) {
        return new PublicToolResult(
                call.getKind(), content.getContentVersion(), content.getRuntimeBundleHash(),
                status, projects, claims, evidence, timeline, questions);
    }
}
