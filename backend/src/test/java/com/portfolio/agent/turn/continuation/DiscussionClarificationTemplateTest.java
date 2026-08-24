package com.portfolio.agent.turn.continuation;

import com.portfolio.agent.turn.planning.DiscussionClarificationTemplate;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscussionClarificationTemplateTest {

    @Test
    void storeRequiresPublicChoicesToExactlyCloseTheTypedFacetSet() {
        ClarificationStore store = new ClarificationStore(
                Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC),
                Duration.ofMinutes(5));
        ClarificationChallenge challenge = new ClarificationChallenge(
                "clarification_discussion", "请选择讨论方向。", List.of(
                new ClarificationChallenge.SingleChoiceField(
                        "field_direction", "讨论方向", true, List.of(
                        new ClarificationChallenge.Choice(
                                "choice_solution", "解决方案")))), List.of());
        ClarificationStore.Record incomplete = new ClarificationStore.Record(
                "conversation-a", new byte[32], "public-1", challenge,
                Map.of("field_direction", Map.of(
                        "choice_solution", "discussion:facet:SOLUTION")),
                Map.of(), new DiscussionClarificationTemplate(
                        "discussion-handle", "project-a",
                        Set.of(UserGoalProposal.Facet.SOLUTION,
                                UserGoalProposal.Facet.VERIFICATION), false));

        assertThatThrownBy(() -> store.save(incomplete))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("discussion clarification binding is invalid");
    }

    @Test
    void templateRequiresExactlyOneRecoveryMode() {
        assertThatThrownBy(() -> new DiscussionClarificationTemplate(
                "discussion-handle", "project-a", Set.of(), false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DiscussionClarificationTemplate(
                "discussion-handle", "project-a",
                Set.of(UserGoalProposal.Facet.SOLUTION), true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
