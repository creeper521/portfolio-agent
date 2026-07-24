package com.portfolio.agent.release;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.portfolio.agent.answer.adapter.retrieval.LocalEmbeddingArtifact;
import com.portfolio.agent.answer.adapter.retrieval.LocalEmbeddingArtifactVerifier;
import com.portfolio.agent.answer.adapter.retrieval.OnnxLocalEmbeddingAdapter;
import com.portfolio.agent.answer.domain.RetrievalPolicy;
import com.portfolio.agent.answer.service.KeywordRetriever;
import com.portfolio.agent.answer.service.ReciprocalRankFusion;
import com.portfolio.agent.answer.service.RetrievalContextValidator;
import com.portfolio.agent.answer.service.RetrievalQueryNormalizer;
import com.portfolio.agent.answer.service.VectorRetriever;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.repository.file.PublicBundleLoader;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import com.portfolio.agent.release.benchmark.RetrievalBenchmarkCaseLoader;
import com.portfolio.agent.release.benchmark.RetrievalBenchmarkEvaluator;
import com.portfolio.agent.release.benchmark.RetrievalBenchmarkMarkdownRenderer;
import com.portfolio.agent.release.benchmark.RetrievalBenchmarkReport;
import com.portfolio.agent.release.benchmark.RetrievalBenchmarkRunMetadata;
import com.portfolio.agent.release.benchmark.RetrievalBenchmarkSuite;
import com.portfolio.agent.release.benchmark.RetrievalComparisonRunner;
import com.portfolio.agent.release.benchmark.RetrievalRouteEvaluation;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;

public final class RetrievalComparisonCli {

    private static final Set<String> OPTION_NAMES = Set.of(
            "--portfolio",
            "--cases",
            "--model-dir",
            "--output-dir",
            "--valid-from"
    );
    private static final String TEMPORARY_PREFIX = ".retrieval-comparison-";

    private RetrievalComparisonCli() {
    }

    public static void main(String[] args) {
        int exitCode = run(
                args,
                RetrievalComparisonCli::executeRealBenchmark,
                System.out,
                System.err
        );
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(
            String[] args,
            BenchmarkExecutor executor,
            PrintStream out,
            PrintStream err
    ) {
        return run(
                args,
                executor,
                out,
                err,
                new DefaultReportFileOperations()
        );
    }

    static int run(
            String[] args,
            BenchmarkExecutor executor,
            PrintStream out,
            PrintStream err,
            ReportFileOperations fileOperations
    ) {
        try {
            Objects.requireNonNull(executor, "executor");
            Objects.requireNonNull(out, "out");
            Objects.requireNonNull(err, "err");
            Objects.requireNonNull(fileOperations, "fileOperations");
            Map<String, String> options = options(args);
            Path portfolioFile = Path.of(required(options, "--portfolio"));
            Path casesFile = Path.of(required(options, "--cases"));
            Path modelDirectory = Path.of(required(options, "--model-dir"));
            Path outputDirectory = Path.of(required(options, "--output-dir"));
            LocalDate validFrom = LocalDate.parse(required(options, "--valid-from"));
            validatePaths(
                    portfolioFile,
                    casesFile,
                    modelDirectory,
                    outputDirectory
            );

            ObjectMapper mapper = canonicalMapper();
            RuntimeContentSnapshot snapshot = loadVerifiedSnapshot(
                    portfolioFile,
                    mapper
            );
            if (snapshot.getRetrievalContent().isEmpty()) {
                throw new IllegalArgumentException(
                        "retrieval comparison requires a seven-file bundle");
            }
            RetrievalBenchmarkSuite suite = new RetrievalBenchmarkCaseLoader(mapper)
                    .load(Files.readAllBytes(casesFile));
            if (!snapshot.getContentVersion().equals(suite.getContentVersion())) {
                throw new IllegalArgumentException(
                        "retrieval comparison contentVersion mismatch");
            }

            ComparisonRequest request = new ComparisonRequest(
                    snapshot,
                    suite,
                    modelDirectory.toAbsolutePath().normalize(),
                    validFrom
            );
            RetrievalBenchmarkReport report = Objects.requireNonNull(
                    executor.execute(request),
                    "benchmark report"
            );
            writeReports(outputDirectory, report, mapper, fileOperations);
            out.println("Retrieval comparison completed.");
            return 0;
        } catch (IOException | RuntimeException exception) {
            err.println("RETRIEVAL_COMPARISON_FAILED");
            return 1;
        }
    }

    private static RetrievalBenchmarkReport executeRealBenchmark(
            ComparisonRequest request
    )
            throws IOException {
        return executeRealBenchmark(
                request,
                directory -> verifiedModel(
                        new LocalEmbeddingArtifactVerifier().verify(directory)),
                (comparisonRequest, policy, model) -> {
                    try (OnnxLocalEmbeddingAdapter queryAdapter =
                            new OnnxLocalEmbeddingAdapter(
                                    comparisonRequest.getModelDirectory(),
                                    model.getQueryInstruction(),
                                    model.getMaxTokens(),
                                    model.getDimension(),
                                    model.getIntraOpThreads(),
                                    model.getInterOpThreads()
                            )) {
                        return new RetrievalComparisonRunner(
                                new RetrievalQueryNormalizer(),
                                new KeywordRetriever(),
                                new VectorRetriever(),
                                new ReciprocalRankFusion(),
                                new RetrievalContextValidator(),
                                queryAdapter
                        ).run(
                                comparisonRequest.getSuite(),
                                comparisonRequest.getSnapshot(),
                                policy
                        );
                    }
                },
                RetrievalPolicy.firstRelease(),
                Clock.systemUTC(),
                System::nanoTime
        );
    }

    static RetrievalBenchmarkReport executeRealBenchmark(
            ComparisonRequest request,
            VerifiedModelProvider modelProvider,
            RealEvaluationExecutor evaluationExecutor,
            RetrievalPolicy policy,
            Clock clock,
            LongSupplier stageTimer
    )
            throws IOException {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(modelProvider, "modelProvider");
        Objects.requireNonNull(evaluationExecutor, "evaluationExecutor");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(stageTimer, "stageTimer");
        Instant startedAt = clock.instant();
        long startedNanos = stageTimer.getAsLong();
        VerifiedModel artifact = modelProvider.verify(
                request.getModelDirectory());
        if (!matchesRetrievalIdentity(
                request.getSnapshot(),
                artifact.getModelId(),
                artifact.getDescriptorSha256(),
                artifact.getDimension(),
                policy.getVersion())) {
            throw new IllegalArgumentException(
                    "retrieval comparison identity mismatch");
        }
        List<RetrievalRouteEvaluation> evaluations =
                evaluationExecutor.run(request, policy, artifact);
        long completedNanos = stageTimer.getAsLong();
        Instant completedAt = clock.instant();
        long durationMillis = Math.max(
                0L,
                (completedNanos - startedNanos) / 1_000_000L
        );
        RetrievalBenchmarkRunMetadata runMetadata =
                new RetrievalBenchmarkRunMetadata(
                        systemProperty("java.version"),
                        systemProperty("java.runtime.name"),
                        systemProperty("java.vendor"),
                        systemProperty("os.name"),
                        systemProperty("os.version"),
                        systemProperty("os.arch"),
                        Runtime.getRuntime().availableProcessors(),
                        startedAt,
                        completedAt,
                        durationMillis,
                        request.getSuite().getSuiteVersion(),
                        request.getSuite().getContentVersion(),
                        request.getRuntimeBundleHash(),
                        policy.getVersion(),
                        artifact.getModelId(),
                        artifact.getDescriptorSha256(),
                        artifact.getDimension()
                );
        return new RetrievalBenchmarkReport(
                request.getSuite().getSuiteVersion(),
                request.getSuite().getContentVersion(),
                request.getRuntimeBundleHash(),
                request.getValidFrom().toString(),
                policy.getVersion(),
                artifact.getDescriptorSha256(),
                evaluations,
                new RetrievalBenchmarkEvaluator().evaluate(evaluations),
                runMetadata
        );
    }

    private static VerifiedModel verifiedModel(
            LocalEmbeddingArtifact artifact
    ) {
        return new VerifiedModel(
                artifact.getModelId(),
                artifact.getDescriptorSha256(),
                artifact.getDimension(),
                artifact.getMaxTokens(),
                artifact.getQueryInstruction(),
                artifact.getIntraOpThreads(),
                artifact.getInterOpThreads()
        );
    }

    private static String systemProperty(String name) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? "unknown" : value;
    }

    static boolean matchesRetrievalIdentity(
            RuntimeContentSnapshot snapshot,
            String modelId,
            String descriptorSha256,
            int dimension,
            String policyVersion
    ) {
        if (snapshot == null || policyVersion == null) {
            return false;
        }
        return snapshot.getRetrievalContent()
                .map(content -> content.getManifest())
                .filter(manifest -> Objects.equals(
                        manifest.getEmbeddingModelId(), modelId))
                .filter(manifest -> Objects.equals(
                        manifest.getEmbeddingArtifactSha256(), descriptorSha256))
                .filter(manifest -> manifest.getDimension() == dimension)
                .filter(manifest -> Objects.equals(
                        manifest.getRetrievalPolicyVersion(), policyVersion))
                .isPresent();
    }

    private static void validatePaths(
            Path portfolioFile,
            Path casesFile,
            Path modelDirectory,
            Path outputDirectory
    ) {
        if (!Files.isRegularFile(portfolioFile)
                || !Files.isRegularFile(casesFile)
                || !Files.isDirectory(modelDirectory)
                || Files.exists(outputDirectory)) {
            throw new IllegalArgumentException(
                    "retrieval comparison input is invalid");
        }
        Path absoluteOutput = outputDirectory.toAbsolutePath().normalize();
        Path parent = absoluteOutput.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IllegalArgumentException(
                    "retrieval comparison output parent is invalid");
        }
    }

    private static RuntimeContentSnapshot loadVerifiedSnapshot(
            Path portfolioFile,
            ObjectMapper mapper
    )
            throws IOException {
        Path absolutePortfolio = portfolioFile.toAbsolutePath().normalize();
        if (!"portfolio.json".equals(
                absolutePortfolio.getFileName().toString())) {
            throw new IllegalArgumentException(
                    "retrieval comparison portfolio must be bundle portfolio.json");
        }
        Path bundleDirectory = absolutePortfolio.getParent();
        if (bundleDirectory == null || !Files.isDirectory(bundleDirectory)) {
            throw new IllegalArgumentException(
                    "retrieval comparison bundle directory is invalid");
        }
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (java.util.stream.Stream<Path> entries =
                Files.list(bundleDirectory)) {
            for (Path entry : entries
                    .filter(path -> Files.isRegularFile(
                            path,
                            LinkOption.NOFOLLOW_LINKS))
                    .sorted()
                    .toList()) {
                files.put(entry.getFileName().toString(), Files.readAllBytes(entry));
            }
        }
        return new PublicBundleLoader(
                mapper,
                new PortfolioSnapshotValidator(),
                Clock.systemUTC()
        ).load(files);
    }

    private static void writeReports(
            Path outputDirectory,
            RetrievalBenchmarkReport report,
            ObjectMapper mapper,
            ReportFileOperations fileOperations
    )
            throws IOException {
        Path absoluteOutput = outputDirectory.toAbsolutePath().normalize();
        Path parent = absoluteOutput.getParent();
        if (parent == null || !Files.isDirectory(parent)
                || Files.exists(absoluteOutput)) {
            throw new IllegalArgumentException(
                    "retrieval comparison output is invalid");
        }
        Path temporary = fileOperations.createTempDirectory(
                parent, TEMPORARY_PREFIX)
                .toRealPath(LinkOption.NOFOLLOW_LINKS);
        boolean moved = false;
        try {
            byte[] json = mapper.writeValueAsBytes(report);
            byte[] markdown = new RetrievalBenchmarkMarkdownRenderer()
                    .render(report)
                    .getBytes(StandardCharsets.UTF_8);
            Path jsonFile = temporary.resolve("comparison.json");
            Path markdownFile = temporary.resolve("comparison.md");
            fileOperations.write(jsonFile, json);
            fileOperations.write(markdownFile, markdown);
            verifyReadback(jsonFile, json, fileOperations);
            verifyReadback(markdownFile, markdown, fileOperations);
            fileOperations.move(
                    temporary,
                    absoluteOutput
            );
            moved = true;
        } finally {
            if (!moved) {
                deleteVerifiedTemporaryDirectory(parent, temporary);
            }
        }
    }

    private static void verifyReadback(
            Path file,
            byte[] expected,
            ReportFileOperations fileOperations
    )
            throws IOException {
        if (!Arrays.equals(expected, fileOperations.readAllBytes(file))) {
            throw new IOException("retrieval comparison report readback failed");
        }
    }

    private static void deleteVerifiedTemporaryDirectory(
            Path expectedParent,
            Path temporary
    )
            throws IOException {
        Path normalizedParent = expectedParent.toAbsolutePath().normalize();
        Path normalizedTemporary = temporary.toAbsolutePath().normalize();
        Path fileName = normalizedTemporary.getFileName();
        boolean verified = normalizedParent.equals(normalizedTemporary.getParent())
                && fileName != null
                && fileName.toString().startsWith(TEMPORARY_PREFIX);
        if (!verified || !Files.exists(
                normalizedTemporary,
                LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (java.util.stream.Stream<Path> paths =
                Files.walk(normalizedTemporary)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static ObjectMapper canonicalMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .build();
    }

    private static Map<String, String> options(String[] args) {
        if (args == null || args.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "retrieval comparison options are invalid");
        }
        Map<String, String> options = new HashMap<>();
        for (int index = 0; index < args.length; index += 2) {
            String name = args[index];
            String value = args[index + 1];
            if (!OPTION_NAMES.contains(name)
                    || value == null
                    || value.isBlank()
                    || value.startsWith("--")
                    || options.put(name, value) != null) {
                throw new IllegalArgumentException(
                        "retrieval comparison options are invalid");
            }
        }
        if (options.size() != OPTION_NAMES.size()) {
            throw new IllegalArgumentException(
                    "retrieval comparison options are invalid");
        }
        return options;
    }

    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "required retrieval comparison option is missing");
        }
        return value;
    }

    @FunctionalInterface
    interface BenchmarkExecutor {

        RetrievalBenchmarkReport execute(ComparisonRequest request)
                throws IOException;
    }

    @FunctionalInterface
    interface VerifiedModelProvider {

        VerifiedModel verify(Path modelDirectory) throws IOException;
    }

    @FunctionalInterface
    interface RealEvaluationExecutor {

        List<RetrievalRouteEvaluation> run(
                ComparisonRequest request,
                RetrievalPolicy policy,
                VerifiedModel model
        ) throws IOException;
    }

    interface ReportFileOperations {

        Path createTempDirectory(Path parent, String prefix)
                throws IOException;

        void write(Path file, byte[] content) throws IOException;

        byte[] readAllBytes(Path file) throws IOException;

        void move(Path source, Path target) throws IOException;
    }

    static final class VerifiedModel {

        private final String modelId;
        private final String descriptorSha256;
        private final int dimension;
        private final int maxTokens;
        private final String queryInstruction;
        private final int intraOpThreads;
        private final int interOpThreads;

        VerifiedModel(
                String modelId,
                String descriptorSha256,
                int dimension,
                int maxTokens,
                String queryInstruction,
                int intraOpThreads,
                int interOpThreads
        ) {
            this.modelId = Objects.requireNonNull(modelId, "modelId");
            this.descriptorSha256 = Objects.requireNonNull(
                    descriptorSha256, "descriptorSha256");
            this.dimension = dimension;
            this.maxTokens = maxTokens;
            this.queryInstruction = Objects.requireNonNull(
                    queryInstruction, "queryInstruction");
            this.intraOpThreads = intraOpThreads;
            this.interOpThreads = interOpThreads;
        }

        String getModelId() { return modelId; }
        String getDescriptorSha256() { return descriptorSha256; }
        int getDimension() { return dimension; }
        int getMaxTokens() { return maxTokens; }
        String getQueryInstruction() { return queryInstruction; }
        int getIntraOpThreads() { return intraOpThreads; }
        int getInterOpThreads() { return interOpThreads; }
    }

    private static final class DefaultReportFileOperations
            implements ReportFileOperations {

        @Override
        public Path createTempDirectory(Path parent, String prefix)
                throws IOException {
            return Files.createTempDirectory(parent, prefix);
        }

        @Override
        public void write(Path file, byte[] content) throws IOException {
            Files.write(file, content);
        }

        @Override
        public byte[] readAllBytes(Path file) throws IOException {
            return Files.readAllBytes(file);
        }

        @Override
        public void move(Path source, Path target) throws IOException {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        }
    }

    static final class ComparisonRequest {

        private final RuntimeContentSnapshot snapshot;
        private final RetrievalBenchmarkSuite suite;
        private final Path modelDirectory;
        private final LocalDate validFrom;

        ComparisonRequest(
                RuntimeContentSnapshot snapshot,
                RetrievalBenchmarkSuite suite,
                Path modelDirectory,
                LocalDate validFrom
        ) {
            this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
            this.suite = Objects.requireNonNull(suite, "suite");
            this.modelDirectory = Objects.requireNonNull(
                    modelDirectory,
                    "modelDirectory"
            );
            this.validFrom = Objects.requireNonNull(validFrom, "validFrom");
        }

        PortfolioSnapshot getPortfolio() {
            return new PortfolioSnapshot(
                    snapshot.getSchemaVersion(),
                    snapshot.getContentVersion(),
                    snapshot.getPublishedAt(),
                    snapshot.getOwner(),
                    snapshot.getProjects(),
                    snapshot.getCases(),
                    snapshot.getClaims(),
                    snapshot.getClaimEvidenceLinks(),
                    snapshot.getQuestions(),
                    snapshot.getApprovedEvidence(),
                    snapshot.getTimeline()
            );
        }

        RuntimeContentSnapshot getSnapshot() {
            return snapshot;
        }

        RetrievalBenchmarkSuite getSuite() {
            return suite;
        }

        Path getModelDirectory() {
            return modelDirectory;
        }

        LocalDate getValidFrom() {
            return validFrom;
        }

        String getRuntimeBundleHash() {
            return snapshot.getRuntimeBundleHash();
        }
    }
}
