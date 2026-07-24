package com.portfolio.agent.release;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portfolio.agent.answer.domain.RetrievalDecisionType;
import com.portfolio.agent.answer.domain.RetrievalPolicy;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.domain.RetrievalManifest;
import com.portfolio.agent.portfolio.domain.RuntimeRetrievalContent;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.release.benchmark.RetrievalBenchmarkCategory;
import com.portfolio.agent.release.benchmark.RetrievalBenchmarkReport;
import com.portfolio.agent.release.benchmark.RetrievalBenchmarkRoute;
import com.portfolio.agent.release.benchmark.RetrievalBenchmarkSplit;
import com.portfolio.agent.release.benchmark.RetrievalRouteEvaluation;
import com.portfolio.agent.portfolio.repository.file.BundleHashCalculator;
import com.portfolio.agent.portfolio.repository.file.KeywordIndexFile;
import com.portfolio.agent.portfolio.repository.file.VectorIndexCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
        writeSevenFileBundle(sourceBundle, bundle);
        portfolio = bundle.resolve("portfolio.json");
        cases = temporary.resolve("cases.json");
        Files.writeString(cases, casesJson("2026-07-23.1"));
        modelDirectory = Files.createDirectory(temporary.resolve("model"));
    }

    @Test
    void rejectsFourFileBundleBeforeExecutingOrPublishing() throws Exception {
        Path sourceBundle = projectRoot().resolve(
                "backend/src/main/resources/public-data/bundle");
        Path legacyBundle = Files.createDirectory(temporary.resolve("legacy-bundle"));
        for (String name : List.of(
                "manifest.json", "portfolio.json", "presentation.json", "checksums.json")) {
            Files.copy(sourceBundle.resolve(name), legacyBundle.resolve(name));
        }
        AtomicInteger executions = new AtomicInteger();
        Path output = temporary.resolve("legacy-output");

        RunResult result = run(validArguments(
                output, legacyBundle.resolve("portfolio.json"), cases, modelDirectory),
                request -> {
                    executions.incrementAndGet();
                    return fixedReport();
                });

        assertThat(result.exitCode).isEqualTo(1);
        assertThat(executions).hasValue(0);
        assertThat(output).doesNotExist();
    }

    @Test
    void rejectsPublishedRetrievalIdentityMismatchesWithoutFallback() {
        RuntimeContentSnapshot snapshot = capturedSnapshot();

        assertThat(RetrievalComparisonCli.matchesRetrievalIdentity(
                snapshot,
                "BAAI/bge-small-zh-v1.5",
                "sha256:model",
                512,
                "retrieval-policy-v1")).isTrue();
        assertThat(RetrievalComparisonCli.matchesRetrievalIdentity(
                snapshot, "other-model", "sha256:model", 512,
                "retrieval-policy-v1")).isFalse();
        assertThat(RetrievalComparisonCli.matchesRetrievalIdentity(
                snapshot, "BAAI/bge-small-zh-v1.5", "sha256:other", 512,
                "retrieval-policy-v1")).isFalse();
        assertThat(RetrievalComparisonCli.matchesRetrievalIdentity(
                snapshot, "BAAI/bge-small-zh-v1.5", "sha256:model", 384,
                "retrieval-policy-v1")).isFalse();
        assertThat(RetrievalComparisonCli.matchesRetrievalIdentity(
                snapshot,
                "BAAI/bge-small-zh-v1.5",
                "sha256:model",
                512,
                "retrieval-policy-v2")).isFalse();
    }

    @Test
    void realExecutorRejectsIdentityMismatchBeforeRunningOrPublishing()
            throws Exception {
        RuntimeContentSnapshot snapshot = capturedSnapshot();
        AtomicInteger executions = new AtomicInteger();
        List<RetrievalComparisonCli.VerifiedModel> mismatchedModels = List.of(
                verifiedModel("wrong-model", "sha256:model", 512),
                verifiedModel(
                        "BAAI/bge-small-zh-v1.5",
                        "sha256:wrong",
                        512),
                verifiedModel(
                        "BAAI/bge-small-zh-v1.5",
                        "sha256:model",
                        384)
        );
        for (RetrievalComparisonCli.VerifiedModel model : mismatchedModels) {
            assertRealExecutorMismatch(snapshot, model, executions);
        }
        assertRealExecutorMismatch(
                snapshotWithPolicy(snapshot, "retrieval-policy-v2"),
                verifiedModel(
                        "BAAI/bge-small-zh-v1.5",
                        "sha256:model",
                        512),
                executions
        );
        assertThat(executions).hasValue(0);
    }

    @Test
    void realExecutorUsesInjectedClockAndTimerForStableRunMetadata()
            throws Exception {
        RuntimeContentSnapshot snapshot = capturedSnapshot();
        RetrievalComparisonCli.ComparisonRequest request =
                new RetrievalComparisonCli.ComparisonRequest(
                        snapshot,
                        new com.portfolio.agent.release.benchmark.RetrievalBenchmarkCaseLoader(
                                new ObjectMapper()).load(Files.readAllBytes(cases)),
                        modelDirectory,
                        java.time.LocalDate.parse("2026-07-23"));
        AtomicLong timer = new AtomicLong(2_000_000L);
        Clock clock = new SequenceClock(
                Instant.parse("2026-07-24T01:00:00Z"),
                Instant.parse("2026-07-24T01:00:02Z"));

        RetrievalBenchmarkReport report =
                RetrievalComparisonCli.executeRealBenchmark(
                        request,
                        ignored -> new RetrievalComparisonCli.VerifiedModel(
                                "BAAI/bge-small-zh-v1.5",
                                "sha256:model",
                                512,
                                256,
                                "",
                                1,
                                1),
                        (comparisonRequest, policy, model) ->
                                List.of(executorEvaluation()),
                        RetrievalPolicy.firstRelease(),
                        clock,
                        () -> timer.getAndAdd(250_000_000L));

        assertThat(report.getRunMetadata().getStartedAt())
                .isEqualTo(Instant.parse("2026-07-24T01:00:00Z"));
        assertThat(report.getRunMetadata().getCompletedAt())
                .isEqualTo(Instant.parse("2026-07-24T01:00:02Z"));
        assertThat(report.getRunMetadata().getDurationMillis()).isEqualTo(250L);
        assertThat(report.getRunMetadata().getModelId())
                .isEqualTo("BAAI/bge-small-zh-v1.5");
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

    @Test
    void jsonWriteReadbackAndAtomicMoveFailuresLeaveNoPublicationOrTemporaryDirectory()
            throws Exception {
        for (FailureStage stage : FailureStage.values()) {
            Path output = temporary.resolve("failed-" + stage.name().toLowerCase());
            RunResult result = run(
                    validArguments(output),
                    request -> fixedReport(),
                    new FaultingReportFileOperations(stage));

            assertThat(result.exitCode).isEqualTo(1);
            assertThat(output).doesNotExist();
            try (java.util.stream.Stream<Path> children = Files.list(temporary)) {
                assertThat(children.map(path -> path.getFileName().toString()))
                        .noneMatch(name -> name.startsWith(
                                ".retrieval-comparison-"));
            }
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

    private RunResult run(
            String[] arguments,
            RetrievalComparisonCli.BenchmarkExecutor executor,
            RetrievalComparisonCli.ReportFileOperations fileOperations
    ) {
        ByteArrayOutputStream standard = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        int exitCode;
        try (PrintStream out = new PrintStream(
                standard, true, StandardCharsets.UTF_8);
                PrintStream err = new PrintStream(
                        error, true, StandardCharsets.UTF_8)) {
            exitCode = RetrievalComparisonCli.run(
                    arguments, executor, out, err, fileOperations);
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

    private RuntimeContentSnapshot capturedSnapshot() {
        AtomicReference<RuntimeContentSnapshot> captured = new AtomicReference<>();
        RunResult result = run(validArguments(temporary.resolve("identity-capture")), request -> {
            captured.set(request.getSnapshot());
            return fixedReport();
        });
        assertThat(result.exitCode).isZero();
        return captured.get();
    }

    private void writeSevenFileBundle(Path source, Path target) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("portfolio.json", Files.readAllBytes(source.resolve("portfolio.json")));
        files.put("presentation.json", Files.readAllBytes(source.resolve("presentation.json")));
        byte[] rag = ("{\"chunkId\":\"chunk-sql-audit-delivery\","
                + "\"contentVersion\":\"2026-07-23.1\","
                + "\"projectSlugs\":[\"sql-audit\"],\"caseSlugs\":[],"
                + "\"claimIds\":[\"claim-sql-audit-delivered\"],"
                + "\"text\":\"SQL audit delivered\","
                + "\"topics\":[\"DELIVERY\"],\"validFrom\":\"2026-07-01\","
                + "\"validUntil\":null,\"contentHash\":\"sha256:chunk\"}\n")
                .getBytes(StandardCharsets.UTF_8);
        files.put("rag-documents.jsonl", rag);
        KeywordIndexFile keyword = new KeywordIndexFile(
                "keyword-index-v1",
                "nfkc-bigram-v1",
                1,
                3.0,
                List.of(new KeywordIndexFile.DocumentEntry(
                        "chunk-sql-audit-delivery", 3, Map.of("sql", 1, "audit", 1))),
                Map.of("sql", 1, "audit", 1));
        files.put("keyword-index.json", mapper.writeValueAsBytes(keyword));
        float[] vector = new float[512];
        vector[0] = 1.0f;
        files.put("vector-index.bin", new VectorIndexCodec().encode(
                Map.of("chunk-sql-audit-delivery", vector), 512));

        ObjectNode manifest = (ObjectNode) mapper.readTree(
                Files.readAllBytes(source.resolve("manifest.json")));
        ObjectNode retrieval = mapper.createObjectNode();
        retrieval.put("strategyVersion", "hybrid-rag-v1");
        retrieval.put("normalizationVersion", "nfkc-bigram-v1");
        retrieval.put("retrievalPolicyVersion", "retrieval-policy-v1");
        retrieval.put("embeddingModelId", "BAAI/bge-small-zh-v1.5");
        retrieval.put("embeddingArtifactSha256", "sha256:model");
        retrieval.put("dimension", 512);
        retrieval.put("documentMaxTokens", 256);
        retrieval.put("vectorNormalization", "L2");
        retrieval.put("similarity", "COSINE");
        retrieval.put("chunkCount", 1);
        retrieval.put("chunkSetHash", BundleHashCalculator.sha256(rag));
        retrieval.put("keywordIndexFormatVersion", "keyword-index-v1");
        retrieval.put("vectorIndexFormatVersion", "vector-index-v1");
        manifest.set("retrieval", retrieval);
        manifest.put("candidatePayloadHash", BundleHashCalculator.candidatePayloadHash(files));
        byte[] manifestBytes = mapper.writeValueAsBytes(manifest);

        ObjectNode checksums = mapper.createObjectNode();
        checksums.put("schemaVersion", manifest.path("schemaVersion").asText());
        checksums.put("contentVersion", manifest.path("contentVersion").asText());
        ObjectNode hashes = mapper.createObjectNode();
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            hashes.put(entry.getKey(), BundleHashCalculator.sha256(entry.getValue()));
        }
        checksums.set("files", hashes);
        files.put("manifest.json", manifestBytes);
        files.put("checksums.json", mapper.writeValueAsBytes(checksums));
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            Files.write(target.resolve(entry.getKey()), entry.getValue());
        }
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

    private static final class SequenceClock extends Clock {

        private final Instant[] values;
        private int index;

        private SequenceClock(Instant... values) {
            this.values = values.clone();
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return values[Math.min(index++, values.length - 1)];
        }
    }

    private static RetrievalRouteEvaluation executorEvaluation() {
        return new RetrievalRouteEvaluation(
                RetrievalBenchmarkRoute.HYBRID,
                "executor-case",
                RetrievalBenchmarkSplit.HOLDOUT,
                RetrievalBenchmarkCategory.EXACT_TERM,
                RetrievalDecisionType.SUFFICIENT,
                RetrievalDecisionType.SUFFICIENT,
                1,
                List.of(),
                List.of()
        );
    }

    private void assertRealExecutorMismatch(
            RuntimeContentSnapshot snapshot,
            RetrievalComparisonCli.VerifiedModel model,
            AtomicInteger executions
    ) throws Exception {
        RetrievalComparisonCli.ComparisonRequest request =
                new RetrievalComparisonCli.ComparisonRequest(
                        snapshot,
                        new com.portfolio.agent.release.benchmark.RetrievalBenchmarkCaseLoader(
                                new ObjectMapper()).load(Files.readAllBytes(cases)),
                        modelDirectory,
                        java.time.LocalDate.parse("2026-07-23"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                RetrievalComparisonCli.executeRealBenchmark(
                        request,
                        ignored -> model,
                        (comparisonRequest, policy, verified) -> {
                            executions.incrementAndGet();
                            return List.of(executorEvaluation());
                        },
                        RetrievalPolicy.firstRelease(),
                        Clock.fixed(
                                Instant.parse("2026-07-24T01:00:00Z"),
                                ZoneId.of("UTC")),
                        () -> 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identity mismatch");
    }

    private RetrievalComparisonCli.VerifiedModel verifiedModel(
            String modelId,
            String descriptorHash,
            int dimension
    ) {
        return new RetrievalComparisonCli.VerifiedModel(
                modelId,
                descriptorHash,
                dimension,
                256,
                "",
                1,
                1
        );
    }

    private RuntimeContentSnapshot snapshotWithPolicy(
            RuntimeContentSnapshot source,
            String policyVersion
    ) {
        RuntimeRetrievalContent retrieval = source.getRetrievalContent()
                .orElseThrow();
        RetrievalManifest manifest = retrieval.getManifest();
        RetrievalManifest changed = new RetrievalManifest(
                manifest.getStrategyVersion(),
                manifest.getNormalizationVersion(),
                policyVersion,
                manifest.getEmbeddingModelId(),
                manifest.getEmbeddingArtifactSha256(),
                manifest.getDimension(),
                manifest.getDocumentMaxTokens(),
                manifest.getVectorNormalization(),
                manifest.getSimilarity(),
                manifest.getChunkCount(),
                manifest.getChunkSetHash(),
                manifest.getKeywordIndexFormatVersion(),
                manifest.getVectorIndexFormatVersion()
        );
        PortfolioSnapshot portfolio = new PortfolioSnapshot(
                source.getSchemaVersion(),
                source.getContentVersion(),
                source.getPublishedAt(),
                source.getOwner(),
                source.getProjects(),
                source.getCases(),
                source.getClaims(),
                source.getClaimEvidenceLinks(),
                source.getQuestions(),
                source.getApprovedEvidence(),
                source.getTimeline()
        );
        return new RuntimeContentSnapshot(
                portfolio,
                source.getRuntimeBundleHash(),
                source.getLoadedAt(),
                new RuntimeRetrievalContent(
                        changed,
                        retrieval.getDocuments(),
                        retrieval.getKeywordIndex(),
                        retrieval.getVectorIndex()
                )
        );
    }

    private enum FailureStage {
        JSON_WRITE,
        JSON_READBACK,
        MARKDOWN_READBACK,
        ATOMIC_MOVE
    }

    private static final class FaultingReportFileOperations
            implements RetrievalComparisonCli.ReportFileOperations {

        private final FailureStage stage;
        private int writes;
        private int reads;

        private FaultingReportFileOperations(FailureStage stage) {
            this.stage = stage;
        }

        @Override
        public Path createTempDirectory(Path parent, String prefix)
                throws IOException {
            return Files.createTempDirectory(parent, prefix);
        }

        @Override
        public void write(Path file, byte[] content) throws IOException {
            writes++;
            if (stage == FailureStage.JSON_WRITE && writes == 1) {
                throw new IOException("injected JSON write failure");
            }
            Files.write(file, content);
        }

        @Override
        public byte[] readAllBytes(Path file) throws IOException {
            reads++;
            if (stage == FailureStage.JSON_READBACK && reads == 1) {
                throw new IOException("injected JSON readback failure");
            }
            if (stage == FailureStage.MARKDOWN_READBACK && reads == 2) {
                throw new IOException("injected Markdown readback failure");
            }
            return Files.readAllBytes(file);
        }

        @Override
        public void move(Path source, Path target) throws IOException {
            if (stage == FailureStage.ATOMIC_MOVE) {
                throw new IOException("injected atomic move failure");
            }
            Files.move(
                    source,
                    target,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
            );
        }
    }
}
