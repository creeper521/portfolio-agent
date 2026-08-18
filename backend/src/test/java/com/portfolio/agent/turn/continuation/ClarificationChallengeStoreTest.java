package com.portfolio.agent.turn.continuation;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClarificationChallengeStoreTest {
    @Test void consumesOpaqueChoiceExactlyOnceWithinBoundConversation() {
        ClarificationStore store = new ClarificationStore(
                Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC),
                Duration.ofMinutes(5));
        ClarificationChallenge challenge = new ClarificationChallenge(
                "clarification-1", "请选择项目", List.of(
                new ClarificationChallenge.SingleChoiceField(
                        "field-subject", "项目", true,
                        List.of(new ClarificationChallenge.Choice("choice-a", "项目 A")))),
                List.of());
        store.save(new ClarificationStore.Record(
                "conversation-1", new byte[]{1, 2, 3}, "public-1", challenge,
                Map.of("field-subject", Map.of("choice-a", "subject:project-a")), Map.of()));

        ClarificationStore.ConsumeResult consumed = store.consume(
                "clarification-1", "conversation-1", new byte[]{1, 2, 3}, "public-1",
                new ClarificationStore.ClarificationAnswer.Choice("choice-a"));
        assertThat(consumed.status()).isEqualTo(ClarificationStore.Status.CONSUMED);
        assertThat(consumed.answer().bindingKey()).isEqualTo("subject:project-a");
        assertThat(store.consume(
                "clarification-1", "conversation-1", new byte[]{1, 2, 3}, "public-1",
                new ClarificationStore.ClarificationAnswer.Choice("choice-a")).status())
                .isEqualTo(ClarificationStore.Status.ALREADY_CONSUMED);
    }

    @Test void wrongCredentialOrChoiceNeverConsumesChallenge() {
        ClarificationStore store = storeWithTextChallenge();
        assertThat(store.consume(
                "clarification-text", "conversation-1", new byte[]{9}, "public-1",
                new ClarificationStore.ClarificationAnswer.Text("补充内容")).status())
                .isEqualTo(ClarificationStore.Status.UNAUTHORIZED);
        assertThat(store.consume(
                "clarification-text", "conversation-1", new byte[]{4}, "public-1",
                new ClarificationStore.ClarificationAnswer.Text("这段文字明显超过二十个字符的限制因此必须被拒绝")).status())
                .isEqualTo(ClarificationStore.Status.INVALID_ANSWER);
        assertThat(store.consume(
                "clarification-text", "conversation-1", new byte[]{4}, "public-1",
                new ClarificationStore.ClarificationAnswer.Text("补充内容")).status())
                .isEqualTo(ClarificationStore.Status.CONSUMED);
    }

    private ClarificationStore storeWithTextChallenge() {
        ClarificationStore store = new ClarificationStore(
                Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC),
                Duration.ofMinutes(5));
        ClarificationChallenge challenge = new ClarificationChallenge(
                "clarification-text", "请补充", List.of(
                new ClarificationChallenge.TextField("field-text", "补充内容", true, 20)), List.of());
        store.save(new ClarificationStore.Record(
                "conversation-1", new byte[]{4}, "public-1", challenge,
                Map.of(), Map.of("field-text", new ClarificationStore.TextBinding("goal:detail", 20))));
        return store;
    }
}
