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
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
        try {
            Objects.requireNonNull(executor, "executor");
            Objects.requireNonNull(out, "out");
            Objects.requireNonNull(err, "err");
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
            writeReports(outputDirectory, report, mapper);
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
        LocalEmbeddingArtifact artifact = new LocalEmbeddingArtifactVerifier()
                .verify(request.getModelDirectory());
        RetrievalPolicy policy = RetrievalPolicy.firstRelease();
        if (!matchesRetrievalIdentity(
                request.getSnapshot(),
                artifact.getModelId(),
                artifact.getDescriptorSha256(),
                artifact.getDimension(),
                policy.getVersion())) {
            throw new IllegalArgumentException(
                    "retrieval comparison identity mismatch");
        }
        List<RetrievalRouteEvaluation> evaluations;
        try (OnnxLocalEmbeddingAdapter queryAdapter =
                new OnnxLocalEmbeddingAdapter(
                        request.getModelDirectory(),
                        artifact.getQueryInstruction(),
                        artifact.getMaxTokens(),
                        artifact.getDimension(),
                        artifact.getIntraOpThreads(),
                        artifact.getInterOpThreads()
                )) {
            evaluations = new RetrievalComparisonRunner(
                    new RetrievalQueryNormalizer(),
                    new KeywordRetriever(),
                    new VectorRetriever(),
                    new ReciprocalRankFusion(),
                    new RetrievalContextValidator(),
                    queryAdapter
            ).run(request.getSuite(), request.getSnapshot(), policy);
        }
        return new RetrievalBenchmarkReport(
                request.getSuite().getSuiteVersion(),
                request.getSuite().getContentVersion(),
                request.getRuntimeBundleHash(),
                request.getValidFrom().toString(),
                policy.getVersion(),
                artifact.getDescriptorSha256(),
                evaluations,
                new RetrievalBenchmarkEvaluator().evaluate(evaluations)
        );
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
            ObjectMapper mapper
    )
            throws IOException {
        Path absoluteOutput = outputDirectory.toAbsolutePath().normalize();
        Path parent = absoluteOutput.getParent();
        if (parent == null || !Files.isDirectory(parent)
                || Files.exists(absoluteOutput)) {
            throw new IllegalArgumentException(
                    "retrieval comparison output is invalid");
        }
        Path temporary = Files.createTempDirectory(parent, TEMPORARY_PREFIX)
                .toRealPath(LinkOption.NOFOLLOW_LINKS);
        boolean moved = false;
        try {
            byte[] json = mapper.writeValueAsBytes(report);
            byte[] markdown = new RetrievalBenchmarkMarkdownRenderer()
                    .render(report)
                    .getBytes(StandardCharsets.UTF_8);
            Path jsonFile = temporary.resolve("comparison.json");
            Path markdownFile = temporary.resolve("comparison.md");
            Files.write(jsonFile, json);
            Files.write(markdownFile, markdown);
            verifyReadback(jsonFile, json);
            verifyReadback(markdownFile, markdown);
            Files.move(
                    temporary,
                    absoluteOutput,
                    StandardCopyOption.ATOMIC_MOVE
            );
            moved = true;
        } finally {
            if (!moved) {
                deleteVerifiedTemporaryDirectory(parent, temporary);
            }
        }
    }

    private static void verifyReadback(Path file, byte[] expected)
            throws IOException {
        if (!Arrays.equals(expected, Files.readAllBytes(file))) {
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

    static final class ComparisonRequest {

        private final RuntimeContentSnapshot snapshot;
        private final RetrievalBenchmarkSuite suite;
        private final Path modelDirectory;
        private final LocalDate validFrom;

        private ComparisonRequest(
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
