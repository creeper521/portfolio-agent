package com.portfolio.agent.evaluation.coverage;

import java.util.List;
import java.util.Set;

/** Names the P5 lanes in the existing evaluation harness; it is not a second harness. */
public final class P5SuiteCatalog {
    private static final List<P5Suite> SUITES = List.of(
            new P5Suite("routing-binding", Set.of("MODEL_OFF", "FAKE_PROVIDER", "LIVE_PROVIDER")),
            new P5Suite("material-support", Set.of("MODEL_OFF", "FAKE_PROVIDER")),
            new P5Suite("cross-domain-synthesis", Set.of("MODEL_OFF", "FAKE_PROVIDER", "LIVE_PROVIDER")),
            new P5Suite("context-version", Set.of("MODEL_OFF", "FAKE_PROVIDER")),
            new P5Suite("failure-degradation", Set.of("MODEL_OFF", "FAKE_PROVIDER")),
            new P5Suite("configuration-retrieval", Set.of("MODEL_OFF", "FAKE_PROVIDER", "LIVE_PROVIDER")));

    private static final Set<String> ZERO_TOLERANCE_METRICS = Set.of(
            "unsupported_relation_publish_rate",
            "source_domain_bleed_rate",
            "portfolio_fact_mutation_rate",
            "invalid_public_reference_publish_rate",
            "cross_version_material_mix_rate",
            "secret_or_private_content_leak");

    private P5SuiteCatalog() { }

    public static List<String> suiteIds() {
        return SUITES.stream().map(P5Suite::getSuiteId).toList();
    }

    public static List<P5Suite> suites() {
        return SUITES;
    }

    public static Set<String> zeroToleranceMetricIds() {
        return ZERO_TOLERANCE_METRICS;
    }

    public static boolean contains(String suiteId) {
        return suiteIds().contains(suiteId);
    }

    public static final class P5Suite {
        private final String suiteId;
        private final Set<String> requiredLanes;

        private P5Suite(String suiteId, Set<String> requiredLanes) {
            this.suiteId = suiteId;
            this.requiredLanes = Set.copyOf(requiredLanes);
        }

        public String getSuiteId() { return suiteId; }
        public Set<String> getRequiredLanes() { return requiredLanes; }
    }
}
