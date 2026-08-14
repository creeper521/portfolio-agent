package com.portfolio.agent.evaluation.coverage;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class P5SuiteCatalogTest {
    @Test void p5UsesSixNamedSuitesInTheExistingHarness() {
        assertThat(P5SuiteCatalog.suiteIds()).containsExactly(
                "routing-binding", "material-support", "cross-domain-synthesis",
                "context-version", "failure-degradation", "configuration-retrieval");
    }

    @Test void p5CatalogCarriesRequiredLanesAndZeroToleranceMetrics() {
        assertThat(P5SuiteCatalog.suites()).hasSize(6);
        assertThat(P5SuiteCatalog.suites().stream()
                .flatMap(suite -> suite.getRequiredLanes().stream()).distinct())
                .contains("MODEL_OFF", "FAKE_PROVIDER", "LIVE_PROVIDER");
        assertThat(P5SuiteCatalog.zeroToleranceMetricIds()).containsExactlyInAnyOrder(
                "unsupported_relation_publish_rate", "source_domain_bleed_rate",
                "portfolio_fact_mutation_rate", "invalid_public_reference_publish_rate",
                "cross_version_material_mix_rate", "secret_or_private_content_leak");
    }
}
