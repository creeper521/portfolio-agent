package com.portfolio.agent.answer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskSourceDomain;

import java.util.List;
import java.util.Objects;

/** Only successfully rendered tasks are present; blocked or insufficient tasks have no pseudo-body. */
public final class CompletedTaskResponse {

    private final String displayIndex;
    private final String goalLabel;
    private final TaskSourceDomain sourceDomain;
    private final ResultPayload resultPayload;

    public CompletedTaskResponse(
            String displayIndex, String goalLabel, TaskSourceDomain sourceDomain, ResultPayload resultPayload) {
        this.displayIndex = requireText(displayIndex, "displayIndex");
        this.goalLabel = requireText(goalLabel, "goalLabel");
        this.sourceDomain = Objects.requireNonNull(sourceDomain, "sourceDomain");
        this.resultPayload = Objects.requireNonNull(resultPayload, "resultPayload");
    }

    public String getDisplayIndex() { return displayIndex; }
    public String getGoalLabel() { return goalLabel; }
    public TaskSourceDomain getSourceDomain() { return sourceDomain; }
    public ResultPayload getResultPayload() { return resultPayload; }

    public static final class ResultPayload {
        private final String kind;
        private final List<ConversationAnswerBlockResponse> blocks;
        private final List<PortfolioRecommendationItemResponse> recommendations;
        private final List<TaskSourceDomain> originDomains;

        public ResultPayload(
                String kind,
                List<ConversationAnswerBlockResponse> blocks,
                List<PortfolioRecommendationItemResponse> recommendations,
                List<TaskSourceDomain> originDomains) {
            this.kind = requireText(kind, "kind");
            this.blocks = blocks == null ? null : List.copyOf(blocks);
            this.recommendations = recommendations == null ? null : List.copyOf(recommendations);
            this.originDomains = originDomains == null ? null : List.copyOf(originDomains);
        }

        public String getKind() { return kind; }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public List<ConversationAnswerBlockResponse> getBlocks() { return blocks; }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public List<PortfolioRecommendationItemResponse> getRecommendations() { return recommendations; }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public List<TaskSourceDomain> getOriginDomains() { return originDomains; }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
