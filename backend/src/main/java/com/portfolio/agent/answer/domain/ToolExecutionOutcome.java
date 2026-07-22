package com.portfolio.agent.answer.domain;

import java.util.List;
import java.util.Objects;

public final class ToolExecutionOutcome {

    private final PublicToolResultStatus status;
    private final List<PublicToolResult> results;

    public ToolExecutionOutcome(
            PublicToolResultStatus status,
            List<PublicToolResult> results
    ) {
        this.status = Objects.requireNonNull(status, "status");
        this.results = List.copyOf(results);
    }

    public PublicToolResultStatus getStatus() { return status; }
    public List<PublicToolResult> getResults() { return results; }

    public List<AnswerKnowledge> getProjects() {
        return results.stream().flatMap(result -> result.getProjects().stream()).distinct().toList();
    }

    public List<AnswerClaimProjection> getClaims() {
        return results.stream().flatMap(result -> result.getClaims().stream()).distinct().toList();
    }

    public List<AnswerEvidence> getEvidence() {
        return results.stream().flatMap(result -> result.getEvidence().stream()).distinct().toList();
    }

    public List<AnswerTimelineEvent> getTimeline() {
        return results.stream().flatMap(result -> result.getTimeline().stream()).distinct().toList();
    }

    public List<AnswerQuestion> getQuestions() {
        return results.stream().flatMap(result -> result.getQuestions().stream()).distinct().toList();
    }
}
