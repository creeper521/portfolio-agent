package com.portfolio.agent.answer.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationProgressTest {

    @Test
    void defensivelyCopiesCoveredTopics() {
        List<ConversationTopic> topics = new ArrayList<>();
        topics.add(ConversationTopic.BACKGROUND);

        ConversationProgress progress = new ConversationProgress(
                topics,
                ConversationGuidanceStage.OPENING);
        topics.add(ConversationTopic.SOLUTION);

        assertThat(progress.getCoveredTopics())
                .containsExactly(ConversationTopic.BACKGROUND);
        assertThatThrownBy(() -> progress.getCoveredTopics().add(
                ConversationTopic.SOLUTION))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void comparesByTopicsAndStage() {
        ConversationProgress first = new ConversationProgress(
                List.of(
                        ConversationTopic.BACKGROUND,
                        ConversationTopic.SOLUTION),
                ConversationGuidanceStage.OPENING);
        ConversationProgress second = new ConversationProgress(
                List.of(
                        ConversationTopic.BACKGROUND,
                        ConversationTopic.SOLUTION),
                ConversationGuidanceStage.OPENING);

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }
}
