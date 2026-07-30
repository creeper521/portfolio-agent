package com.portfolio.agent.selection.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.repository.file.PublicBundleLoader;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PortfolioSelectionBenchmarkCliTest {
    @TempDir
    Path temporary;

    @Test
    void writesDeterministicAggregateJsonAndMarkdown() throws Exception {
        Path cases = copyFixture("portfolio-selection-cases.json");
        Path observations = temporary.resolve("observations.json");
        Files.writeString(observations, """
                {
                  "releaseVersion": "2026-07-29.1",
                  "observations": []
                }
                """, StandardCharsets.UTF_8);
        Path first = temporary.resolve("first.json");
        Path second = temporary.resolve("second.json");
        Path markdown = temporary.resolve("report.md");

        PortfolioSelectionBenchmarkCli.run(new String[] {
                "--cases", cases.toString(), "--observations", observations.toString(),
                "--bundle", bundle().toString(), "--json", first.toString(),
                "--markdown", markdown.toString()
        });
        PortfolioSelectionBenchmarkCli.run(new String[] {
                "--cases", cases.toString(), "--observations", observations.toString(),
                "--bundle", bundle().toString(), "--json", second.toString()
        });

        assertThat(Files.readAllBytes(first)).isEqualTo(Files.readAllBytes(second));
        assertThat(Files.readString(first)).contains("\"R0\"", "\"UNAVAILABLE\"");
        assertThat(Files.readString(markdown)).contains("No R0–R4 improvement is claimed");
    }

    @Test
    void rejectsUnknownCaseCrossReleaseAndUnsupportedObservation() throws Exception {
        Path cases = copyFixture("portfolio-selection-cases.json");
        Path observations = temporary.resolve("invalid.json");
        Files.writeString(observations, """
                {
                  "releaseVersion": "2026-07-29.1",
                  "observations": [{
                    "route": "R4",
                    "caseId": "UNKNOWN",
                    "releaseVersion": "other",
                    "rankedCandidateSubjectIds": ["subject-a"],
                    "selectedSubjectIds": ["subject-a"],
                    "elapsedMilliseconds": 1,
                    "retrievalMode": "HYBRID",
                    "selectionMode": "EXHAUSTIVE",
                    "state": "AVAILABLE",
                    "sufficient": true,
                    "selectedSubjects": [{
                      "subjectId": "subject-a",
                      "releaseVersion": "other",
                      "capabilities": [],
                      "approvedEvidenceValid": true,
                      "supported": false
                    }]
                  }]
                }
                """, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> PortfolioSelectionBenchmarkCli.run(new String[] {
                "--cases", cases.toString(), "--observations", observations.toString(),
                "--bundle", bundle().toString(),
                "--json", temporary.resolve("out.json").toString()
        })).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesToOverwriteAnyInputFile() throws Exception {
        Path cases = copyFixture("portfolio-selection-cases.json");
        Path observations = temporary.resolve("observations.json");
        Files.writeString(observations, """
                {"releaseVersion":"2026-07-29.1","observations":[]}
                """, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> PortfolioSelectionBenchmarkCli.run(new String[] {
                "--cases", cases.toString(), "--observations", observations.toString(),
                "--bundle", bundle().toString(), "--json", observations.toString()
        })).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("output");
    }

    @Test
    void explicitlyVerifiesFileAndPostgresSnapshotsThroughSupplierSeam() throws Exception {
        Path cases = copyFixture("portfolio-selection-cases.json");
        Path observations = temporary.resolve("migration-observations.json");
        Path output = temporary.resolve("migration-report.json");
        Files.writeString(observations, """
                {"releaseVersion":"2026-07-29.1","observations":[]}
                """, StandardCharsets.UTF_8);
        RuntimeContentSnapshot postgresSnapshot = loadBundle();

        PortfolioSelectionBenchmarkCli.run(new String[] {
                "--cases", cases.toString(), "--observations", observations.toString(),
                "--bundle", bundle().toString(), "--json", output.toString(),
                "--verify-migration", "true"
        }, () -> postgresSnapshot);

        JsonNode report = new ObjectMapper().readTree(output.toFile());
        assertThat(report.path("migrationIntegrity").path("available").asBoolean()).isTrue();
        assertThat(report.path("migrationIntegrity").path("completeMatch").asBoolean()).isTrue();
        assertThat(report.path("migrationIntegrity").path("score").asDouble()).isEqualTo(1.0);
    }

    private Path bundle() {
        return Path.of("src/main/resources/public-data/bundle");
    }

    private RuntimeContentSnapshot loadBundle() throws Exception {
        Set<String> names = Set.of(
                "manifest.json", "portfolio.json", "presentation.json", "rag-documents.jsonl",
                "keyword-index.json", "vector-index.bin", "checksums.json");
        Map<String, byte[]> files = new HashMap<>();
        for (String name : names) {
            files.put(name, Files.readAllBytes(bundle().resolve(name)));
        }
        return new PublicBundleLoader(
                new ObjectMapper().findAndRegisterModules(),
                new PortfolioSnapshotValidator(), Clock.systemUTC()).load(files);
    }

    private Path copyFixture(String name) throws Exception {
        Path path = temporary.resolve(name);
        try (java.io.InputStream stream = getClass().getResourceAsStream(
                "/retrieval-benchmark/" + name)) {
            Files.copy(stream, path);
        }
        return path;
    }
}
