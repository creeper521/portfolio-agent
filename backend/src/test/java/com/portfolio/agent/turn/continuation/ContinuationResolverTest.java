package com.portfolio.agent.turn.continuation;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ContinuationResolverTest {
    private final Instant now = Instant.parse("2026-08-18T00:00:00Z");
    private final ContinuationResolver resolver = new ContinuationResolver(
            Clock.fixed(now, ZoneOffset.UTC));

    @Test void explicitHandleWinsAndResultItemMustBelongToIt() {
        ContinuationContext.Recommendation context = recommendation("ctx-a");
        assertThat(resolver.resolve(
                "ctx-a", "item-a", ContinuationContext.Kind.RECOMMENDATION,
                "conversation-1", "public-1", List.of(context)).status())
                .isEqualTo(ContinuationResolver.Status.RESOLVED);
        assertThat(resolver.resolve(
                "ctx-a", "item-b", ContinuationContext.Kind.RECOMMENDATION,
                "conversation-1", "public-1", List.of(context)).status())
                .isEqualTo(ContinuationResolver.Status.RESULT_ITEM_INVALID);
    }

    @Test void noHandleRequiresExactlyOneCompatibleContextAndNeverChoosesMostRecent() {
        assertThat(resolver.resolve(
                null, null, ContinuationContext.Kind.RECOMMENDATION,
                "conversation-1", "public-1",
                List.of(recommendation("ctx-a"), recommendation("ctx-b"))).status())
                .isEqualTo(ContinuationResolver.Status.CLARIFICATION_REQUIRED);
    }

    private ContinuationContext.Recommendation recommendation(String handle) {
        return new ContinuationContext.Recommendation(
                handle, "conversation-1", "public-1", now.plusSeconds(300),
                false, Set.of("project-a"), Set.of(), Set.of(), Set.of(), 1, null,
                List.of(new ContinuationContext.ResultItem("item-a", "project-a")));
    }
}
