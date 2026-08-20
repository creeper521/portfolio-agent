package com.portfolio.agent.turn.continuation;

import com.portfolio.agent.turn.planning.BlockedGoalTemplate;
import com.portfolio.agent.turn.planning.ClarificationProposal;
import com.portfolio.agent.turn.planning.GoalKind;
import com.portfolio.agent.turn.planning.GoalRequestedOutput;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                Map.of("field-subject", Map.of("choice-a", "subject:PROJECT:project-a")), Map.of(),
                new BlockedGoalTemplate(
                        GoalKind.PORTFOLIO_FACT, List.of(), Set.of(GoalRequestedOutput.OVERVIEW),
                        Set.of(UserGoalProposal.Facet.OVERVIEW), Set.of(), null, Set.of(),
                        ClarificationProposal.Field.SUBJECT,
                        Set.of(ClarificationProposal.Field.SUBJECT), 1)));

        ClarificationStore.ConsumeResult consumed = store.consume(
                "clarification-1", "conversation-1", new byte[]{1, 2, 3}, "public-1",
                new ClarificationStore.ClarificationAnswer.Choice("choice-a"));
        assertThat(consumed.status()).isEqualTo(ClarificationStore.Status.CONSUMED);
        assertThat(consumed.answer().bindingKey()).isEqualTo("subject:PROJECT:project-a");
        assertThat(store.consume(
                "clarification-1", "conversation-1", new byte[]{1, 2, 3}, "public-1",
                new ClarificationStore.ClarificationAnswer.Choice("choice-a")).status())
                .isEqualTo(ClarificationStore.Status.ALREADY_CONSUMED);
    }

    @Test void wrongCredentialOrChoiceNeverConsumesChallenge() {
        ClarificationStore store = storeWithSizeChallenge();
        assertThat(store.consume(
                "clarification-size", "conversation-1", new byte[]{9}, "public-1",
                new ClarificationStore.ClarificationAnswer.Choice("choice_size_2")).status())
                .isEqualTo(ClarificationStore.Status.UNAUTHORIZED);
        assertThat(store.consume(
                "clarification-size", "conversation-1", new byte[]{4}, "public-1",
                new ClarificationStore.ClarificationAnswer.Choice("choice_invalid")).status())
                .isEqualTo(ClarificationStore.Status.INVALID_ANSWER);
        assertThat(store.consume(
                "clarification-size", "conversation-1", new byte[]{4}, "public-1",
                new ClarificationStore.ClarificationAnswer.Choice("choice_size_2")).status())
                .isEqualTo(ClarificationStore.Status.CONSUMED);
    }

    @Test void rejectsFieldKindAndBindingThatDoNotMatchBlockedGoal() {
        ClarificationStore store = new ClarificationStore(
                Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC),
                Duration.ofMinutes(5));
        ClarificationChallenge challenge = new ClarificationChallenge(
                "clarification-invalid", "请补充", List.of(
                new ClarificationChallenge.TextField("field-text", "数量", true, 20)), List.of());
        ClarificationStore.Record inconsistent = new ClarificationStore.Record(
                "conversation-1", new byte[]{4}, "public-1", challenge,
                Map.of(), Map.of("field-text", new ClarificationStore.TextBinding(
                "goal:detail", 20)), BlockedGoalTemplate.recommendation(
                null, Set.of(), ClarificationProposal.Field.REQUESTED_SIZE));

        assertThatThrownBy(() -> store.save(inconsistent))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match blocked goal");
    }

    @Test void rejectsDuplicateFieldIdsBeforeBindingMapCollapse() {
        ClarificationStore store = new ClarificationStore(
                Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC),
                Duration.ofMinutes(5));
        ClarificationChallenge challenge = new ClarificationChallenge(
                "clarification-duplicate", "请选择数量", List.of(
                new ClarificationChallenge.SingleChoiceField(
                        "same-field", "数量一", true, List.of(
                        new ClarificationChallenge.Choice("choice_size_1", "1 个项目"))),
                new ClarificationChallenge.SingleChoiceField(
                        "same-field", "数量二", true, List.of(
                        new ClarificationChallenge.Choice("choice_size_2", "2 个项目")))), List.of());
        ClarificationStore.Record record = new ClarificationStore.Record(
                "conversation-1", new byte[]{4}, "public-1", challenge,
                Map.of("same-field", Map.of("choice_size_2", "size:2")), Map.of(),
                BlockedGoalTemplate.recommendation(
                        null, Set.of(), ClarificationProposal.Field.REQUESTED_SIZE));

        assertThatThrownBy(() -> store.save(record))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique");
    }

    @Test void expiresAtFiveMinutesAndCleanupHonorsTheSharedBatchBudget() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-19T00:00:00Z"));
        ClarificationStore store = new ClarificationStore(clock, Duration.ofMinutes(5));
        store.save(sizeRecord("clarification-expiry-a"));
        store.save(sizeRecord("clarification-expiry-b"));

        clock.advance(Duration.ofMinutes(5));

        assertThat(store.consume(
                "clarification-expiry-a", "conversation-1", new byte[]{4}, "public-1",
                new ClarificationStore.ClarificationAnswer.Choice("choice_size_2")).status())
                .isEqualTo(ClarificationStore.Status.EXPIRED);
        assertThat(store.cleanup(clock.instant(), 1)).isEqualTo(1);
        assertThat(store.cleanup(clock.instant(), 1)).isEqualTo(1);
        assertThat(store.cleanup(clock.instant(), 1)).isZero();
    }

    private ClarificationStore storeWithSizeChallenge() {
        ClarificationStore store = new ClarificationStore(
                Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC),
                Duration.ofMinutes(5));
        ClarificationChallenge challenge = new ClarificationChallenge(
                "clarification-size", "请选择数量", List.of(
                new ClarificationChallenge.SingleChoiceField(
                        "field-size", "推荐数量", true, List.of(
                        new ClarificationChallenge.Choice("choice_size_2", "2 个项目")))), List.of());
        store.save(new ClarificationStore.Record(
                "conversation-1", new byte[]{4}, "public-1", challenge,
                Map.of("field-size", Map.of("choice_size_2", "size:2")), Map.of(),
                BlockedGoalTemplate.recommendation(
                        null, Set.of(), ClarificationProposal.Field.REQUESTED_SIZE)));
        return store;
    }

    private ClarificationStore.Record sizeRecord(String clarificationId) {
        ClarificationChallenge challenge = new ClarificationChallenge(
                clarificationId, "请选择数量", List.of(
                new ClarificationChallenge.SingleChoiceField(
                        "field-size", "推荐数量", true, List.of(
                        new ClarificationChallenge.Choice("choice_size_2", "2 个项目")))), List.of());
        return new ClarificationStore.Record(
                "conversation-1", new byte[]{4}, "public-1", challenge,
                Map.of("field-size", Map.of("choice_size_2", "size:2")), Map.of(),
                BlockedGoalTemplate.recommendation(
                        null, Set.of(), ClarificationProposal.Field.REQUESTED_SIZE));
    }

    private static final class MutableClock extends Clock {
        private Instant current;
        private MutableClock(Instant current) { this.current = current; }
        private void advance(Duration duration) { current = current.plus(duration); }
        @Override public Instant instant() { return current; }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
    }
}
