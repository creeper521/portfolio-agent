package com.portfolio.agent.selection.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.repository.file.PublicBundleLoader;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PortfolioSelectionBenchmarkCliBoundaryTest {
    private static final Set<String> BUNDLE_FILES = Set.of(
            "manifest.json", "portfolio.json", "presentation.json", "rag-documents.jsonl",
            "keyword-index.json", "vector-index.bin", "checksums.json");

    @TempDir
    Path temporary;

    @Test
    void rejectsEveryBundleInputAsAnOutputAndRejectsSharedJsonMarkdownOutput() throws Exception {
        for (String bundleFile : BUNDLE_FILES) {
            Inputs inputs = inputs("bundle-conflict-" + bundleFile.replace('.', '-'));
            assertThatThrownBy(() -> PortfolioSelectionBenchmarkCli.run(new String[] {
                    "--cases", inputs.getCases().toString(),
                    "--observations", inputs.getObservations().toString(),
                    "--bundle", inputs.getBundle().toString(),
                    "--json", inputs.getBundle().resolve(bundleFile).toString()
            })).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("output");
        }

        Inputs inputs = inputs("output-conflict");
        Path shared = temporary.resolve("shared-output");
        assertThatThrownBy(() -> PortfolioSelectionBenchmarkCli.run(new String[] {
                "--cases", inputs.getCases().toString(),
                "--observations", inputs.getObservations().toString(),
                "--bundle", inputs.getBundle().toString(),
                "--json", shared.toString(),
                "--markdown", shared.toString()
        })).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("output");
    }

    @Test
    void strictlyParsesMigrationBoolean() throws Exception {
        Inputs inputs = inputs("strict-boolean");
        assertThatThrownBy(() -> PortfolioSelectionBenchmarkCli.run(new String[] {
                "--cases", inputs.getCases().toString(),
                "--observations", inputs.getObservations().toString(),
                "--bundle", inputs.getBundle().toString(),
                "--json", temporary.resolve("strict.json").toString(),
                "--verify-migration", "tru"
        })).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void executableBoundaryCatchesRuntimeFailuresWithoutLeakingDetails() {
        String secret = "jdbc:postgresql://db.internal/private?password=secret";
        ByteArrayOutputStream errors = new ByteArrayOutputStream();

        int status = PortfolioSelectionBenchmarkCli.execute(
                new String[] {
                        "--cases", "cases.json", "--observations", "observations.json",
                        "--bundle", "bundle", "--json", "report.json",
                        "--verify-migration", "true"
                },
                new PrintStream(errors, true, StandardCharsets.UTF_8),
                arguments -> {
                    throw new IllegalStateException(secret);
                });

        assertThat(status).isEqualTo(2);
        assertThat(errors.toString(StandardCharsets.UTF_8))
                .isEqualTo("PORTFOLIO_BENCHMARK_INPUT_INVALID" + System.lineSeparator())
                .doesNotContain("jdbc", "password", "secret", "cases.json");
    }

    @Test
    void opensPostgresRuntimeOnlyWhenMigrationVerificationIsTrue() throws Exception {
        Inputs withoutMigration = inputs("without-migration");
        AtomicBoolean opened = new AtomicBoolean();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();

        int withoutMigrationStatus = PortfolioSelectionBenchmarkCli.execute(
                arguments(withoutMigration, "false"),
                new PrintStream(errors, true, StandardCharsets.UTF_8),
                arguments -> {
                    opened.set(true);
                    throw new AssertionError("database runtime must remain disabled");
                });

        assertThat(withoutMigrationStatus).isZero();
        assertThat(opened).isFalse();

        Inputs withMigration = inputs("with-migration");
        RuntimeContentSnapshot postgresSnapshot = loadBundle(withMigration.getBundle());
        AtomicBoolean closed = new AtomicBoolean();
        int withMigrationStatus = PortfolioSelectionBenchmarkCli.execute(
                arguments(withMigration, "true"),
                new PrintStream(errors, true, StandardCharsets.UTF_8),
                arguments -> {
                    opened.set(true);
                    assertThat(arguments).contains(
                            "--spring.main.web-application-type=none",
                            "--portfolio.database.public.enabled=true");
                    return new PortfolioSelectionBenchmarkCli.MigrationRuntime() {
                        @Override
                        public PostgresMigrationSnapshotSupplier snapshotSupplier() {
                            return () -> postgresSnapshot;
                        }

                        @Override
                        public void close() {
                            closed.set(true);
                        }
                    };
                });

        assertThat(withMigrationStatus).isZero();
        assertThat(opened).isTrue();
        assertThat(closed).isTrue();
    }

    private String[] arguments(Inputs inputs, String verify) {
        return new String[] {
                "--cases", inputs.getCases().toString(),
                "--observations", inputs.getObservations().toString(),
                "--bundle", inputs.getBundle().toString(),
                "--json", inputs.getOutput().toString(),
                "--verify-migration", verify
        };
    }

    private Inputs inputs(String directory) throws Exception {
        Path root = Files.createDirectories(temporary.resolve(directory));
        Path cases = root.resolve("cases.json");
        try (java.io.InputStream stream = getClass().getResourceAsStream(
                "/retrieval-benchmark/portfolio-selection-cases.json")) {
            Files.copy(stream, cases);
        }
        Path observations = root.resolve("observations.json");
        Files.writeString(
                observations,
                "{\"releaseVersion\":\"2026-07-29.1\",\"observations\":[]}",
                StandardCharsets.UTF_8);
        Path bundle = Files.createDirectories(root.resolve("bundle"));
        Path source = Path.of("src/main/resources/public-data/bundle");
        for (String name : BUNDLE_FILES) {
            Files.copy(source.resolve(name), bundle.resolve(name));
        }
        return new Inputs(cases, observations, bundle, root.resolve("report.json"));
    }

    private RuntimeContentSnapshot loadBundle(Path root) throws Exception {
        Map<String, byte[]> files = new HashMap<>();
        for (String name : BUNDLE_FILES) {
            files.put(name, Files.readAllBytes(root.resolve(name)));
        }
        return new PublicBundleLoader(
                new ObjectMapper().findAndRegisterModules(),
                new PortfolioSnapshotValidator(),
                Clock.systemUTC()).load(files);
    }

    private static final class Inputs {
        private final Path cases;
        private final Path observations;
        private final Path bundle;
        private final Path output;

        private Inputs(Path cases, Path observations, Path bundle, Path output) {
            this.cases = Objects.requireNonNull(cases, "cases");
            this.observations = Objects.requireNonNull(observations, "observations");
            this.bundle = Objects.requireNonNull(bundle, "bundle");
            this.output = Objects.requireNonNull(output, "output");
        }

        private Path getCases() {
            return cases;
        }

        private Path getObservations() {
            return observations;
        }

        private Path getBundle() {
            return bundle;
        }

        private Path getOutput() {
            return output;
        }
    }
}
