package com.portfolio.agent.turn.continuation;

import com.portfolio.agent.turn.planning.DiscussionSelectionTemplate;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DiscussionSelectionTemplateTest {

    @Test
    void oneConsumeChoiceCanOnlySelectAnActualRecommendationResult() {
        ClarificationStore store = new ClarificationStore(
                Clock.systemUTC(), Duration.ofMinutes(5));
        ClarificationChallenge challenge = new ClarificationChallenge(
                "clarification_selection_1",
                "请选择推荐项目",
                List.of(new ClarificationChallenge.SingleChoiceField(
                        "field_selection",
                        "推荐项目",
                        true,
                        List.of(
                                new ClarificationChallenge.Choice(
                                        "choice_a", "项目 A"),
                                new ClarificationChallenge.Choice(
                                        "choice_b", "项目 B")))),
                List.of());
        DiscussionSelectionTemplate template =
                new DiscussionSelectionTemplate(
                        "recommendation_handle_123",
                        Set.of("item-a", "item-b"));
        store.save(new ClarificationStore.Record(
                "conversation-1", new byte[32], "release-1",
                challenge,
                Map.of("field_selection", Map.of(
                        "choice_a", "result-item:item-a",
                        "choice_b", "result-item:item-b")),
                Map.of(), template));

        ClarificationStore.ConsumeResult consumed = store.consume(
                "clarification_selection_1",
                "conversation-1", new byte[32], "release-1",
                new ClarificationStore.ClarificationAnswer.Choice("choice_b"));

        assertThat(consumed.status())
                .isEqualTo(ClarificationStore.Status.CONSUMED);
        assertThat(consumed.record().resumeTemplate())
                .isSameAs(template);
        assertThat(consumed.answer().bindingKey())
                .isEqualTo("result-item:item-b");
    }
}
