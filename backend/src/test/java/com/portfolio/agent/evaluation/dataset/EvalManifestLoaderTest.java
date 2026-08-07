package com.portfolio.agent.evaluation.dataset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvalManifestLoaderTest {

    @TempDir
    Path tempDir;

    private final EvalManifestLoader loader = new EvalManifestLoader();

    private Path writeManifest(String json) throws Exception {
        Files.createDirectories(tempDir.resolve("cases/calibration"));
        Files.writeString(tempDir.resolve("cases/calibration/core.v1.json"),
                "{}", StandardCharsets.UTF_8);
        Path manifest = tempDir.resolve("manifest.json");
        Files.writeString(manifest, json, StandardCharsets.UTF_8);
        return manifest;
    }

    @Test
    void rejectsUnknownFields() throws Exception {
        Path manifest = writeManifest("""
                {
                  "schemaVersion": "1.0",
                  "suiteId": "suite",
                  "datasetVersion": "2026-08-06.1",
                  "trackedCaseFiles": ["cases/calibration/core.v1.json"],
                  "unexpected": true
                }
                """);

        assertThatThrownBy(() -> loader.load(manifest))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDuplicateTrackedPaths() throws Exception {
        Path manifest = writeManifest("""
                {
                  "schemaVersion": "1.0",
                  "suiteId": "suite",
                  "datasetVersion": "2026-08-06.1",
                  "trackedCaseFiles": ["cases/calibration/core.v1.json", "cases/calibration/core.v1.json"]
                }
                """);

        assertThatThrownBy(() -> loader.load(manifest))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsRepositoryStoredChallenge() throws Exception {
        Path manifest = writeManifest("""
                {
                  "schemaVersion": "1.0",
                  "suiteId": "suite",
                  "datasetVersion": "2026-08-06.1",
                  "trackedCaseFiles": ["cases/calibration/core.v1.json"],
                  "challenge": {"source": "EXTERNAL_ONLY", "pathStoredInRepository": true}
                }
                """);

        assertThatThrownBy(() -> loader.load(manifest))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPathEscapingManifestRoot() throws Exception {
        Path manifest = writeManifest("""
                {
                  "schemaVersion": "1.0",
                  "suiteId": "suite",
                  "datasetVersion": "2026-08-06.1",
                  "trackedCaseFiles": ["../outside.json"]
                }
                """);

        assertThatThrownBy(() -> loader.load(manifest))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingTrackedFiles() throws Exception {
        Path manifest = writeManifest("""
                {
                  "schemaVersion": "1.0",
                  "suiteId": "suite",
                  "datasetVersion": "2026-08-06.1",
                  "trackedCaseFiles": ["cases/holdout/missing.v1.json"]
                }
                """);

        assertThatThrownBy(() -> loader.load(manifest))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loadsValidManifestInDeclaredOrder() throws Exception {
        Path manifest = writeManifest("""
                {
                  "schemaVersion": "1.0",
                  "suiteId": "suite",
                  "datasetVersion": "2026-08-06.1",
                  "trackedCaseFiles": ["cases/calibration/core.v1.json"],
                  "generationRuleFiles": []
                }
                """);

        EvalManifestLoader.EvalManifest loaded = loader.load(manifest);

        assertThat(loaded.getDatasetVersion()).isEqualTo("2026-08-06.1");
        assertThat(loaded.getTrackedCaseFiles()).hasSize(1);
        assertThat(loaded.getTrackedCaseFiles().get(0).getFileName().toString())
                .isEqualTo("core.v1.json");
    }
}
