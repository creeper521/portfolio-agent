package com.portfolio.agent.answer.domain;

import java.util.List;
import java.util.Objects;

public final class ConversationProgress {

    private final List<ConversationTopic> coveredTopics;
    private final ConversationGuidanceStage stage;

    public ConversationProgress(
            List<ConversationTopic> coveredTopics,
            ConversationGuidanceStage stage
    ) {
        this.coveredTopics = List.copyOf(coveredTopics);
        this.stage = Objects.requireNonNull(stage, "stage");
    }

    public List<ConversationTopic> getCoveredTopics() {
        return coveredTopics;
    }

    public ConversationGuidanceStage getStage() {
        return stage;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConversationProgress that)) {
            return false;
        }
        return Objects.equals(coveredTopics, that.coveredTopics)
                && stage == that.stage;
    }

    @Override
    public int hashCode() {
        return Objects.hash(coveredTopics, stage);
    }

    @Override
    public String toString() {
        return "ConversationProgress{" +
                "coveredTopics=" + coveredTopics +
                ", stage=" + stage +
                '}';
    }
}
