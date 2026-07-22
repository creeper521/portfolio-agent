package com.portfolio.agent.answer.domain;

import java.util.List;
import java.util.Objects;

public final class PublicToolResult {

    private final ToolKind kind;
    private final String contentVersion;
    private final String runtimeBundleHash;
    private final PublicToolResultStatus status;
    private final List<AnswerKnowledge> projects;
    private final List<AnswerClaimProjection> claims;
    private final List<AnswerEvidence> evidence;
    private final List<AnswerTimelineEvent> timeline;
    private final List<AnswerQuestion> questions;

    public PublicToolResult(
            ToolKind kind,
            String contentVersion,
            String runtimeBundleHash,
            PublicToolResultStatus status,
            List<AnswerKnowledge> projects,
            List<AnswerClaimProjection> claims,
            List<AnswerEvidence> evidence,
            List<AnswerTimelineEvent> timeline,
            List<AnswerQuestion> questions
    ) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.contentVersion = contentVersion;
        this.runtimeBundleHash = runtimeBundleHash;
        this.status = Objects.requireNonNull(status, "status");
        this.projects = List.copyOf(projects);
        this.claims = List.copyOf(claims);
        this.evidence = List.copyOf(evidence);
        this.timeline = List.copyOf(timeline);
        this.questions = List.copyOf(questions);
    }

    public ToolKind getKind() { return kind; }
    public String getContentVersion() { return contentVersion; }
    public String getRuntimeBundleHash() { return runtimeBundleHash; }
    public PublicToolResultStatus getStatus() { return status; }
    public List<AnswerKnowledge> getProjects() { return projects; }
    public List<AnswerClaimProjection> getClaims() { return claims; }
    public List<AnswerEvidence> getEvidence() { return evidence; }
    public List<AnswerTimelineEvent> getTimeline() { return timeline; }
    public List<AnswerQuestion> getQuestions() { return questions; }
}
