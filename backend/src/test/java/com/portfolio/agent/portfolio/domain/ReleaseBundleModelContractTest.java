package com.portfolio.agent.portfolio.domain;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseBundleModelContractTest {

    @Test
    void releaseModelsAreExplicitImmutableClassesRatherThanRecords() {
        BundleCounts counts = new BundleCounts(1, 5, 1, 5, 1, 1);
        ReleaseManifest manifest = new ReleaseManifest(
                "2.0", "2026-07-21.1",
                OffsetDateTime.parse("2026-07-21T00:00:00+08:00"),
                OffsetDateTime.parse("2026-07-20T23:55:00+08:00"),
                "0.1.0", "portfolio.json", "presentation.json",
                "APR-1", "sha256:approval", "sha256:candidate",
                "checksums.json", counts);

        assertThat(ReleaseManifest.class.isRecord()).isFalse();
        assertThat(BundleCounts.class.isRecord()).isFalse();
        assertThat(PresentationSnapshot.class.isRecord()).isFalse();
        assertThat(manifest.getCounts()).isEqualTo(counts);
        assertThat(manifest.getFactsFile()).isEqualTo("portfolio.json");
    }
}
