package com.portfolio.agent.answer.intelligence.retrieval;

import java.util.Objects;
import java.util.Optional;

public final class EffectiveRetrievalPlan {
    private final RetrievalIntent intent;
    private final CorpusBackend primaryBackend;
    private final SearchStrategy primaryStrategy;
    private final SearchStrategy fallbackStrategy;
    private final CorpusBackend fallbackBackend;
    private final String expectedContentVersion;

    public EffectiveRetrievalPlan(
            RetrievalIntent intent,
            CorpusBackend primaryBackend,
            SearchStrategy primaryStrategy,
            SearchStrategy fallbackStrategy,
            CorpusBackend fallbackBackend,
            String expectedContentVersion) {
        this.intent = Objects.requireNonNull(intent, "intent");
        this.primaryBackend = Objects.requireNonNull(primaryBackend, "primaryBackend");
        this.primaryStrategy = Objects.requireNonNull(primaryStrategy, "primaryStrategy");
        this.fallbackStrategy = fallbackStrategy;
        this.fallbackBackend = fallbackBackend;
        this.expectedContentVersion = requireText(expectedContentVersion);
        if ((fallbackStrategy == null) != (fallbackBackend == null)) {
            throw new IllegalArgumentException("fallback strategy and backend must be paired");
        }
    }

    public RetrievalIntent getIntent() { return intent; }
    public CorpusBackend getPrimaryBackend() { return primaryBackend; }
    public SearchStrategy getPrimaryStrategy() { return primaryStrategy; }
    public Optional<SearchStrategy> getFallbackStrategy() { return Optional.ofNullable(fallbackStrategy); }
    public Optional<CorpusBackend> getFallbackBackend() { return Optional.ofNullable(fallbackBackend); }
    public String getExpectedContentVersion() { return expectedContentVersion; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof EffectiveRetrievalPlan that)) return false;
        return intent == that.intent && primaryBackend == that.primaryBackend
                && primaryStrategy == that.primaryStrategy
                && fallbackStrategy == that.fallbackStrategy
                && fallbackBackend == that.fallbackBackend
                && expectedContentVersion.equals(that.expectedContentVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(intent, primaryBackend, primaryStrategy,
                fallbackStrategy, fallbackBackend, expectedContentVersion);
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("expectedContentVersion is required");
        return value.trim();
    }
}
