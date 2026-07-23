package com.portfolio.agent.portfolio.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReleaseBundleModelContractTest {

    @Test
    void releaseModelsAreExplicitImmutableClassesRatherThanRecords() {
        BundleCounts counts = new BundleCounts(1, 0, 5, 1, 5, 1, 1);
        ReleaseManifest manifest = new ReleaseManifest(
                "2.0", "2026-07-21.1",
                OffsetDateTime.parse("2026-07-21T00:00:00+08:00"),
                OffsetDateTime.parse("2026-07-20T23:55:00+08:00"),
                "0.1.0", "portfolio.json", "presentation.json",
                "APR-1", "sha256:approval", "sha256:candidate",
                "checksums.json", counts, null);

        assertThat(ReleaseManifest.class.isRecord()).isFalse();
        assertThat(BundleCounts.class.isRecord()).isFalse();
        assertThat(PresentationSnapshot.class.isRecord()).isFalse();
        assertThat(manifest.getCounts()).isEqualTo(counts);
        assertThat(manifest.getCounts().getCases()).isZero();
        assertThat(manifest.getFactsFile()).isEqualTo("portfolio.json");
        assertThat(manifest.getRetrieval()).isNull();
    }

    @Test
    void retrievalManifestUsesTheClosedC2aContract() {
        RetrievalManifest retrieval = new RetrievalManifest(
                "hybrid-rag-v1", "nfkc-bigram-v1", "retrieval-policy-v1",
                "BAAI/bge-small-zh-v1.5", "sha256:model",
                512, 256, "L2", "COSINE", 3, "sha256:chunks",
                "keyword-index-v1", "vector-index-v1");

        assertThat(RetrievalManifest.class.isRecord()).isFalse();
        assertThat(retrieval.getEmbeddingModelId()).isEqualTo("BAAI/bge-small-zh-v1.5");
        assertThat(retrieval.getDimension()).isEqualTo(512);
        assertThat(retrieval.getChunkCount()).isEqualTo(3);
        assertThat(retrieval.getVectorNormalization()).isEqualTo("L2");
    }

    @Test
    void legacyMissingOrNullCasesCountNormalizesToZero() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BundleCounts missing = mapper.readValue("""
                {"projects":1,"claims":1,"evidence":1,"claimEvidenceLinks":1,
                "timelineEvents":1,"questionPresets":1}
                """, BundleCounts.class);
        BundleCounts explicitNull = mapper.readValue("""
                {"projects":1,"cases":null,"claims":1,"evidence":1,
                "claimEvidenceLinks":1,"timelineEvents":1,"questionPresets":1}
                """, BundleCounts.class);

        assertThat(missing.getCases()).isZero();
        assertThat(explicitNull.getCases()).isZero();
    }

    @Test
    void rejectsNegativeCasesCount() {
        assertThatThrownBy(() -> new BundleCounts(1, -1, 1, 1, 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cases");
    }
}
