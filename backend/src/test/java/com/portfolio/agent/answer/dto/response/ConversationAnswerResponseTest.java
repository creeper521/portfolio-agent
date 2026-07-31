package com.portfolio.agent.answer.dto.response;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationGuidanceStage;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationProgress;
import com.portfolio.agent.answer.domain.ConversationSourceScope;
import com.portfolio.agent.answer.domain.ConversationTopic;
import com.portfolio.agent.answer.domain.GenerationMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationAnswerResponseTest {

    @Test
    void exposesProductionGenerationMetadata() {
        ConversationAnswerResult result = new ConversationAnswerResult(
                "turn-1",
                "2026-07-27.1",
                ConversationIntent.PORTFOLIO_GROUNDED,
                ConversationAnswerScope.PORTFOLIO,
                AnswerResolution.ANSWERED,
                "title",
                List.of(new ConversationAnswerBlock(
                        ConversationSourceScope.PORTFOLIO,
                        "content",
                        List.of("claim"),
                        List.of("evidence"))),
                List.of(),
                true,
                GenerationMode.FALLBACK,
                AnswerSource.PRESET,
                "MODEL_UNAVAILABLE_FALLBACK",
                new ConversationProgress(
                        List.of(
                                ConversationTopic.BACKGROUND,
                                ConversationTopic.SOLUTION),
                        ConversationGuidanceStage.OPENING));

        ConversationAnswerResponse response = new ConversationAnswerResponse(result);

        assertThat(response.getGenerationMode()).isEqualTo(GenerationMode.FALLBACK);
        assertThat(response.getAnswerSource()).isEqualTo(AnswerSource.PRESET);
        assertThat(response.getNoticeCode()).isEqualTo("MODEL_UNAVAILABLE_FALLBACK");
        assertThat(response.getCoveredTopics()).containsExactly(
                ConversationTopic.BACKGROUND,
                ConversationTopic.SOLUTION);
        assertThat(response.getGuidanceStage())
                .isEqualTo(ConversationGuidanceStage.OPENING);
    }
}
