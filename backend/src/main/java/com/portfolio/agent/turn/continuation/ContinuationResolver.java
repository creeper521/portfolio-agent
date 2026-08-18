package com.portfolio.agent.turn.continuation;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/** Explicit handle wins; otherwise exactly one compatible active context is required. */
public final class ContinuationResolver {
    private final Clock clock;
    public ContinuationResolver(Clock clock) { this.clock = Objects.requireNonNull(clock, "clock"); }

    public Resolution resolve(
            String explicitHandle, String resultItemId,
            ContinuationContext.Kind expectedKind, String conversationId,
            String currentReleaseId, List<ContinuationContext> activeContexts) {
        Objects.requireNonNull(expectedKind, "expectedKind");
        List<ContinuationContext> candidates = List.copyOf(activeContexts).stream()
                .filter(value -> value.getConversationId().equals(conversationId))
                .filter(value -> value.getKind() == expectedKind)
                .filter(value -> clock.instant().isBefore(value.getExpiresAt()))
                .toList();
        ContinuationContext selected;
        if (explicitHandle != null) {
            selected = candidates.stream().filter(value ->
                    value.getContextHandle().equals(explicitHandle)).findFirst().orElse(null);
            if (selected == null) return Resolution.of(Status.NOT_FOUND);
        } else {
            if (candidates.isEmpty()) return Resolution.of(Status.NOT_FOUND);
            if (candidates.size() != 1) return Resolution.of(Status.CLARIFICATION_REQUIRED);
            selected = candidates.getFirst();
        }
        if (!selected.getContentReleaseId().equals(currentReleaseId)) {
            return new Resolution(Status.REBIND_REQUIRED, selected);
        }
        if (resultItemId != null) {
            if (!(selected instanceof ContinuationContext.Recommendation recommendation)
                    || recommendation.getSelectedResults().stream().noneMatch(value ->
                    value.resultItemId().equals(resultItemId))) {
                return Resolution.of(Status.RESULT_ITEM_INVALID);
            }
        }
        return new Resolution(Status.RESOLVED, selected);
    }

    public record Resolution(Status status, ContinuationContext context) {
        public Resolution { Objects.requireNonNull(status, "status"); }
        static Resolution of(Status status) { return new Resolution(status, null); }
    }
    public enum Status {
        RESOLVED, NOT_FOUND, CLARIFICATION_REQUIRED, REBIND_REQUIRED, RESULT_ITEM_INVALID
    }
}
