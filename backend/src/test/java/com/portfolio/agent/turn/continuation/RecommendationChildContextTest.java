package com.portfolio.agent.turn.continuation;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecommendationChildContextTest {
    @Test void childKeepsOrNarrowsScopeAndRecordsParent() {
        Instant expires = Instant.parse("2026-08-18T00:10:00Z");
        ContinuationContext.Recommendation parent = new ContinuationContext.Recommendation(
                "ctx-parent", "conversation-1", "public-1", expires,
                false, Set.of("project-a", "project-b"), Set.of("JAVA"), Set.of(), Set.of(),
                2, null, List.of(
                new ContinuationContext.ResultItem("item-a", "project-a"),
                new ContinuationContext.ResultItem("item-b", "project-b")));
        ContinuationContext.Recommendation child = parent.child(
                "ctx-child", "public-2", expires.plusSeconds(60), Set.of("project-a"),
                Set.of("BACKEND"), Set.of(), Set.of("project-b"), 1,
                List.of(new ContinuationContext.ResultItem("item-child-a", "project-a")));
        assertThat(child.getAuthorizedSubjectIds()).containsExactly("project-a");
        assertThat(child.getConstraints()).containsExactlyInAnyOrder("JAVA", "BACKEND");
        assertThat(child.getParentContextHandle()).isEqualTo("ctx-parent");
    }

    @Test void childCannotExpandParentAuthorization() {
        Instant expires = Instant.parse("2026-08-18T00:10:00Z");
        ContinuationContext.Recommendation parent = new ContinuationContext.Recommendation(
                "ctx-parent", "conversation-1", "public-1", expires,
                false, Set.of("project-a"), Set.of(), Set.of(), Set.of(), 1, null,
                List.of(new ContinuationContext.ResultItem("item-a", "project-a")));
        assertThatThrownBy(() -> parent.child(
                "ctx-child", "public-2", expires, Set.of("project-a", "project-b"),
                Set.of(), Set.of(), Set.of(), 1,
                List.of(new ContinuationContext.ResultItem("item-b", "project-b"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
