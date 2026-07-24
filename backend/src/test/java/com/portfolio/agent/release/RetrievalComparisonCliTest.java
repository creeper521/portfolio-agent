package com.portfolio.agent.release;

import com.portfolio.agent.release.benchmark.RetrievalBenchmarkReport;
import com.portfolio.agent.portfolio.repository.file.BundleHashCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalComparisonCliTest {

    @TempDir
    Path temporary;

    private Path portfolio;
    private Path cases;
    private Path modelDirectory;

    @BeforeEach
    void prepareValidInputs() throws Exception {
        Path sourceBundle = projectRoot().resolve(
                "backend/src/main/resources/public-data/bundle");
        Path bundle = Files.createDirectory(temporary.resolve("bundle"));
        for (String name : List.of(
                "manifest.json",
                "portfolio.json",
                "presentation.json",
                "checksums.json")) {
            Files.copy(sourceBundle.resolve(name), bundle.resolve(name));
        }
        portfolio = bundle.resolve("portfolio.json");
        cases = temporary.resolve("cases.json");
        Files.writeString(cases, casesJson("2026-07-23.1"));
        modelDirectory = Files.createDirectory(temporary.resolve("model"));
    }

    @Test
    void rejectsDuplicateMissingUnknownAndNonTwoTokenOptions() {
        List<String[]> invalidArguments = List.of(
                new String[]{
                        "--portfolio", portfolio.toString(),
                        "--portfolio", portfolio.toString(),
                        "--cases", cases.toString(),
                        "--model-dir", modelDirectory.toString(),
                        "--output-dir", temporary.resolve("duplicate").toString(),
                        "--valid-from", "2026-07-23"
                },
                new String[]{
                        "--portfolio", portfolio.toString(),
                        "--cases", cases.toString(),
                        "--model-dir", modelDirectory.toString(),
                        "--output-dir", temporary.resolve("missing").toString()
                },
                new String[]{
                        "--portfolio", portfolio.toString(),
                        "--cases", cases.toString(),
                        "--model-dir", modelDirectory.toString(),
                        "--output-dir", temporary.resolve("unknown").toString(),
                        "--valid-from", "2026-07-23",
                        "--extra", "value"
                },
                new String[]{
                        "--portfolio", portfolio.toString(),
                        "--cases", cases.toString(),
                        "--model-dir", modelDirectory.toString(),
                        "--output-dir", temporary.resolve("odd").toString(),
                        "--valid-from"
                }
        );

        for (String[] arguments : invalidArguments) {
            RunResult result = run(arguments, request -> fixedReport());

            assertThat(result.exitCode).isEqualTo(1);
            assertThat(result.out).isEmpty();
            assertThat(result.err).isEqualTo(
                    "RETRIEVAL_COMPARISON_FAILED" + System.lineSeparator());
        }
    }

    @Test
    void rejectsMissingPortfolioCasesModelAndPreExistingOutput() throws Exception {
        Path existingOutput = Files.createDirectory(temporary.resolve("existing-output"));
        List<String[]> invalidArguments = List.of(
                validArguments(temporary.resolve("missing-portfolio-output"),
                        temporary.resolve("missing-portfolio"), cases, modelDirectory),
                validArguments(temporary.resolve("missing-cases-output"),
                        portfolio, temporary.resolve("missing-cases"), modelDirectory),
                validArguments(temporary.resolve("missing-model-output"),
                        portfolio, cases, temporary.resolve("missing-model")),
                validArguments(existingOutput, portfolio, cases, modelDirectory)
        );

        for (String[] arguments : invalidArguments) {
            RunResult result = run(arguments, request -> fixedReport());

            assertThat(result.exitCode).isEqualTo(1);
            assertThat(result.err).isEqualTo(
                    "RETRIEVAL_COMPARISON_FAILED" + System.lineSeparator());
        }
    }

    @Test
    void rejectsInvalidDateContentVersionMismatchAndMissingOutputParent()
            throws Exception {
        Path mismatchedCases = temporary.resolve("mismatched-cases.json");
        Files.writeString(mismatchedCases, casesJson("2026-07-24.1"));
        List<String[]> invalidArguments = List.of(
                arguments(
                        portfolio,
                        cases,
                        modelDirectory,
                        temporary.resolve("invalid-date"),
                        "2026-02-30"),
                arguments(
                        portfolio,
                        mismatchedCases,
                        modelDirectory,
                        temporary.resolve("mismatch"),
                        "2026-07-23"),
                arguments(
                        portfolio,
                        cases,
                        modelDirectory,
                        temporary.resolve("missing-parent").resolve("output"),
                        "2026-07-23")
        );

        for (String[] arguments : invalidArguments) {
            RunResult result = run(arguments, request -> fixedReport());

            assertThat(result.exitCode).isEqualTo(1);
            assertThat(result.err).isEqualTo(
                    "RETRIEVAL_COMPARISON_FAILED" + System.lineSeparator());
            assertThat(Path.of(arguments[7])).doesNotExist();
        }
    }

    @Test
    void writesBothReportsAtomicallyThroughFakeExecutor() throws Exception {
        Path output = temporary.resolve("comparison");
        AtomicReference<RetrievalComparisonCli.ComparisonRequest> captured =
                new AtomicReference<>();

        RunResult result = run(validArguments(output), request -> {
            captured.set(request);
            return fixedReport(
                    request.getRuntimeBundleHash(),
                    request.getValidFrom().toString()
            );
        });

        assertThat(result.exitCode).isZero();
        assertThat(result.out).isEqualTo(
                "Retrieval comparison completed." + System.lineSeparator());
        assertThat(result.err).isEmpty();
        assertThat(output.resolve("comparison.json")).isRegularFile();
        assertThat(output.resolve("comparison.md")).isRegularFile();
        assertThat(Files.readString(output.resolve("comparison.json")))
                .contains("\"suiteVersion\":\"retrieval-benchmark-v2\"",
                        "\"contentVersion\":\"2026-07-23.1\"",
                        "\"runtimeBundleHash\":\"" + expectedRuntimeBundleHash() + "\"",
                        "\"snapshotValidFrom\":\"2026-07-23\"");
        assertThat(Files.readString(output.resolve("comparison.md")))
                .startsWith("# Retrieval Baseline Comparison\n")
                .contains(
                        "- Verified runtime Bundle hash: `"
                                + expectedRuntimeBundleHash() + "`",
                        "- Snapshot validFrom: `2026-07-23`"
                );
        assertThat(captured.get().getPortfolio().getContentVersion())
                .isEqualTo("2026-07-23.1");
        assertThat(captured.get().getSuite().getContentVersion())
                .isEqualTo("2026-07-23.1");
        assertThat(captured.get().getModelDirectory()).isEqualTo(modelDirectory);
        assertThat(captured.get().getValidFrom().toString()).isEqualTo("2026-07-23");
        assertThat(captured.get().getRuntimeBundleHash())
                .isEqualTo(expectedRuntimeBundleHash())
                .isNotEqualTo(BundleHashCalculator.sha256(Files.readAllBytes(portfolio)));
    }

    @Test
    void rejectsTamperedChecksummedBundleBeforeExecutingOrPublishing()
            throws Exception {
        Files.writeString(
                portfolio,
                Files.readString(portfolio).replace(
                        "\"sql-audit\"",
                        "\"tampered-sql-audit\""
                )
        );
        AtomicInteger executions = new AtomicInteger();
        Path output = temporary.resolve("tampered-comparison");

        RunResult result = run(validArguments(output), request -> {
            executions.incrementAndGet();
            return fixedReport();
        });

        assertThat(result.exitCode).isEqualTo(1);
        assertThat(result.err).isEqualTo(
                "RETRIEVAL_COMPARISON_FAILED" + System.lineSeparator());
        assertThat(executions).hasValue(0);
        assertThat(output).doesNotExist();
    }

    @Test
    void executorFailureLeavesNoOutputOrTemporaryDirectory() throws Exception {
        Path output = temporary.resolve("failed-comparison");

        RunResult result = run(validArguments(output), request -> {
            throw new IllegalStateException("benchmark failed");
        });

        assertThat(result.exitCode).isEqualTo(1);
        assertThat(result.err).isEqualTo(
                "RETRIEVAL_COMPARISON_FAILED" + System.lineSeparator());
        assertThat(output).doesNotExist();
        try (java.util.stream.Stream<Path> children = Files.list(temporary)) {
            assertThat(children.map(path -> path.getFileName().toString()))
                    .noneMatch(name -> name.startsWith(".retrieval-comparison-"));
        }
    }

    private RunResult run(
            String[] arguments,
            RetrievalComparisonCli.BenchmarkExecutor executor
    ) {
        ByteArrayOutputStream standard = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        int exitCode;
        try (PrintStream out = new PrintStream(standard, true, StandardCharsets.UTF_8);
                PrintStream err = new PrintStream(error, true, StandardCharsets.UTF_8)) {
            exitCode = RetrievalComparisonCli.run(arguments, executor, out, err);
        }
        return new RunResult(
                exitCode,
                standard.toString(StandardCharsets.UTF_8),
                error.toString(StandardCharsets.UTF_8)
        );
    }

    private String[] validArguments(Path output) {
        return validArguments(output, portfolio, cases, modelDirectory);
    }

    private String[] validArguments(
            Path output,
            Path portfolioPath,
            Path casesPath,
            Path modelPath
    ) {
        return arguments(
                portfolioPath,
                casesPath,
                modelPath,
                output,
                "2026-07-23"
        );
    }

    private String[] arguments(
            Path portfolioPath,
            Path casesPath,
            Path modelPath,
            Path output,
            String validFrom
    ) {
        return new String[]{
                "--portfolio", portfolioPath.toString(),
                "--cases", casesPath.toString(),
                "--model-dir", modelPath.toString(),
                "--output-dir", output.toString(),
                "--valid-from", validFrom
        };
    }

    private RetrievalBenchmarkReport fixedReport() {
        return fixedReport(
                "sha256:runtime",
                "2026-07-23"
        );
    }

    private RetrievalBenchmarkReport fixedReport(
            String runtimeBundleHash,
            String snapshotValidFrom
    ) {
        return new RetrievalBenchmarkReport(
                "retrieval-benchmark-v2",
                "2026-07-23.1",
                runtimeBundleHash,
                snapshotValidFrom,
                "retrieval-policy-v1",
                "sha256:model",
                List.of(),
                Map.of()
        );
    }

    private String casesJson(String contentVersion) {
        return """
                {
                  "suiteVersion": "retrieval-benchmark-v2",
                  "contentVersion": "%s",
                  "cases": [{
                    "caseId": "negative-unknown",
                    "split": "CALIBRATION",
                    "category": "OUT_OF_SCOPE",
                    "subjectType": "PROJECT",
                    "subjectSlug": "sql-audit",
                    "query": "unknown",
                    "expectedClaimIds": [],
                    "expectedChunkIds": [],
                    "expectedDecision": "INSUFFICIENT"
                  }]
                }
                """.formatted(contentVersion);
    }

    private String expectedRuntimeBundleHash() throws Exception {
        Path bundle = portfolio.getParent();
        return BundleHashCalculator.runtimeBundleHash(
                Files.readAllBytes(bundle.resolve("manifest.json")),
                Files.readAllBytes(bundle.resolve("checksums.json"))
        );
    }

    private Path projectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return Files.isDirectory(current.resolve("backend"))
                ? current
                : current.getParent();
    }

    private static final class RunResult {

        private final int exitCode;
        private final String out;
        private final String err;

        private RunResult(int exitCode, String out, String err) {
            this.exitCode = exitCode;
            this.out = out;
            this.err = err;
        }
    }
}
