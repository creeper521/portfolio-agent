package com.portfolio.agent.selection.benchmark;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.portfolio.agent.portfolio.domain.Claim;
import com.portfolio.agent.portfolio.domain.ClaimEvidenceLink;
import com.portfolio.agent.portfolio.domain.EvidenceRecord;
import com.portfolio.agent.portfolio.domain.EvidenceStatus;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.PortfolioAgentApplication;
import com.portfolio.agent.portfolio.repository.PublicPortfolioRepository;
import com.portfolio.agent.portfolio.repository.file.PublicBundleLoader;
import com.portfolio.agent.portfolio.repository.postgres.PostgresPublicPortfolioRepository;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import java.io.PrintStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Explicit opt-in offline CLI. Input observations contain public stable IDs only;
 * output contains aggregate metrics and no query text, vector, path, or governance ID.
 */
public final class PortfolioSelectionBenchmarkCli {
    private PortfolioSelectionBenchmarkCli() {
    }

    public static void main(String[] args) {
        int status = execute(args, System.err, PortfolioSelectionBenchmarkCli::openPostgresRuntime);
        if (status != 0) {
            System.exit(status);
        }
    }

    static int execute(
            String[] args, PrintStream error, MigrationRuntimeFactory migrationRuntimeFactory) {
        try {
            if (migrationVerificationRequested(args)) {
                try (MigrationRuntime runtime =
                             migrationRuntimeFactory.open(postgresApplicationArguments(args))) {
                    run(args, runtime.snapshotSupplier());
                }
            } else {
                run(args);
            }
            return 0;
        } catch (IOException | RuntimeException exception) {
            error.println("PORTFOLIO_BENCHMARK_INPUT_INVALID");
            return 2;
        }
    }

    public static void run(String[] args) throws IOException {
        run(args, null);
    }

    public static void run(
            String[] args, PostgresMigrationSnapshotSupplier postgresSnapshotSupplier)
            throws IOException {
        Map<String, String> options = parse(args);
        ensureOutputDoesNotOverwriteInput(options);
        ObjectMapper mapper = new ObjectMapper()
                .findAndRegisterModules()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(SerializationFeature.INDENT_OUTPUT);
        PortfolioSelectionBenchmarkSuite suite = mapper.readValue(
                Path.of(required(options, "--cases")).toFile(),
                PortfolioSelectionBenchmarkSuite.class);
        PortfolioSelectionObservationSet observationSet = mapper.readValue(
                Path.of(required(options, "--observations")).toFile(),
                PortfolioSelectionObservationSet.class);
        RuntimeContentSnapshot publicSnapshot = loadBundle(
                Path.of(required(options, "--bundle")), mapper);
        List<PortfolioSelectionObservation> canonicalObservations =
                validateAndCanonicalize(suite, observationSet, publicSnapshot);
        MigrationIntegrityResult migrationIntegrity = migrationIntegrity(
                options, mapper, publicSnapshot, postgresSnapshotSupplier);
        PortfolioSelectionBenchmarkReport report = new PortfolioSelectionBenchmarkEvaluator()
                .evaluate(suite.getReleaseVersion(), suite.getCases(),
                        canonicalObservations, migrationIntegrity);
        byte[] json = mapper.writeValueAsBytes(report);
        Files.write(Path.of(required(options, "--json")), json);
        if (options.containsKey("--markdown")) {
            Files.writeString(
                    Path.of(options.get("--markdown")),
                    new PortfolioSelectionBenchmarkMarkdownRenderer().render(report),
                    StandardCharsets.UTF_8);
        }
    }

    private static List<PortfolioSelectionObservation> validateAndCanonicalize(
            PortfolioSelectionBenchmarkSuite suite, PortfolioSelectionObservationSet observations,
            RuntimeContentSnapshot snapshot) {
        if (!suite.getReleaseVersion().equals(observations.getReleaseVersion())) {
            throw new IllegalArgumentException("observation release does not match frozen suite");
        }
        Set<String> caseIds = new HashSet<>();
        suite.getCases().forEach(value -> {
            if (!caseIds.add(value.getId())) {
                throw new IllegalArgumentException("duplicate benchmark case");
            }
        });
        Set<String> keys = new HashSet<>();
        Map<String, Set<String>> capabilities = new HashMap<>();
        Set<String> subjects = new HashSet<>();
        snapshot.getProjects().forEach(value -> subjects.add(value.getId()));
        snapshot.getCases().forEach(value -> subjects.add(value.getId()));
        for (Claim claim : snapshot.getClaims()) {
            capabilities.computeIfAbsent(claim.getSubjectId(), ignored -> new HashSet<>())
                    .addAll(claim.getTopics());
        }
        Set<String> approvedEvidence = new HashSet<>();
        for (EvidenceRecord evidence : snapshot.getApprovedEvidence()) {
            if (evidence.getPublicStatus() == EvidenceStatus.APPROVED) {
                approvedEvidence.add(evidence.getId());
            }
        }
        Map<String, String> claimSubjects = new HashMap<>();
        snapshot.getClaims().forEach(claim -> claimSubjects.put(claim.getId(), claim.getSubjectId()));
        Set<String> subjectsWithApprovedEvidence = new HashSet<>();
        for (ClaimEvidenceLink link : snapshot.getClaimEvidenceLinks()) {
            if (approvedEvidence.contains(link.getEvidenceId())
                    && claimSubjects.containsKey(link.getClaimId())) {
                subjectsWithApprovedEvidence.add(claimSubjects.get(link.getClaimId()));
            }
        }
        List<PortfolioSelectionObservation> canonical = new java.util.ArrayList<>();
        for (PortfolioSelectionObservation observation : observations.getObservations()) {
            String key = observation.getRoute() + "\u0000" + observation.getCaseId();
            if (!caseIds.contains(observation.getCaseId())
                    || !suite.getReleaseVersion().equals(observation.getReleaseVersion())
                    || !keys.add(key)) {
                throw new IllegalArgumentException("observation is outside closed case/route/release set");
            }
            if (!subjects.containsAll(observation.getRankedCandidateSubjectIds())
                    || !subjects.containsAll(observation.getSelectedSubjectIds())) {
                throw new IllegalArgumentException("observation references unknown public subject");
            }
            List<SelectedSubjectObservation> selected = new java.util.ArrayList<>();
            for (SelectedSubjectObservation subject : observation.getSelectedSubjects()) {
                Set<String> actualCapabilities =
                        Set.copyOf(capabilities.getOrDefault(subject.getSubjectId(), Set.of()));
                boolean actualEvidenceValid =
                        subjectsWithApprovedEvidence.contains(subject.getSubjectId());
                if (!suite.getReleaseVersion().equals(subject.getReleaseVersion())
                        || !subject.getCapabilities().equals(actualCapabilities)
                        || subject.isApprovedEvidenceValid() != actualEvidenceValid
                        || !subject.isSupported()) {
                    throw new IllegalArgumentException("observation does not match public Bundle catalog");
                }
                selected.add(new SelectedSubjectObservation(
                        subject.getSubjectId(), suite.getReleaseVersion(), actualCapabilities,
                        actualEvidenceValid, true));
            }
            canonical.add(new PortfolioSelectionObservation(
                    observation.getRoute(), observation.getCaseId(), observation.getReleaseVersion(),
                    observation.getRankedCandidateSubjectIds(), observation.getSelectedSubjectIds(),
                    observation.getElapsedMilliseconds(), observation.getRetrievalMode(),
                    observation.getSelectionMode(), observation.getState(), observation.getErrorCode(),
                    observation.isSufficient(), selected));
        }
        return List.copyOf(canonical);
    }

    private static Map<String, String> parse(String[] args) {
        if (args.length < 8 || args.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "usage: --cases <file> --observations <file> --json <file> [--markdown <file>]");
        }
        Set<String> allowed = Set.of(
                "--cases", "--observations", "--bundle", "--json", "--markdown",
                "--verify-migration");
        Map<String, String> options = new HashMap<>();
        for (int index = 0; index < args.length; index += 2) {
            if (!allowed.contains(args[index]) || options.putIfAbsent(args[index], args[index + 1]) != null) {
                throw new IllegalArgumentException("unknown or duplicate option");
            }
        }
        return options;
    }

    private static RuntimeContentSnapshot loadBundle(Path root, ObjectMapper mapper) throws IOException {
        Set<String> names = Set.of(
                "manifest.json", "portfolio.json", "presentation.json", "rag-documents.jsonl",
                "keyword-index.json", "vector-index.bin", "checksums.json");
        Map<String, byte[]> files = new HashMap<>();
        for (String name : names) {
            files.put(name, Files.readAllBytes(root.resolve(name)));
        }
        return new PublicBundleLoader(
                mapper, new PortfolioSnapshotValidator(), Clock.systemUTC()).load(files);
    }

    private static MigrationIntegrityResult migrationIntegrity(
            Map<String, String> options,
            ObjectMapper mapper,
            RuntimeContentSnapshot fileSnapshot,
            PostgresMigrationSnapshotSupplier supplier) {
        boolean requested = strictBoolean(
                options.getOrDefault("--verify-migration", "false"), "--verify-migration");
        if (!requested) {
            return MigrationIntegrityResult.unavailable();
        }
        if (supplier == null) {
            throw new IllegalArgumentException(
                    "migration verification requested without PostgreSQL snapshot supplier");
        }
        RuntimeContentSnapshot postgresSnapshot = supplier.get();
        return new PublicSnapshotMigrationIntegrityService(mapper)
                .compare(fileSnapshot, postgresSnapshot);
    }

    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing required option " + name);
        }
        return value;
    }

    private static void ensureOutputDoesNotOverwriteInput(Map<String, String> options) {
        Set<Path> inputs = new HashSet<>();
        inputs.add(normalized(required(options, "--cases")));
        inputs.add(normalized(required(options, "--observations")));
        Path bundle = normalized(required(options, "--bundle"));
        for (String name : bundleFileNames()) {
            inputs.add(bundle.resolve(name).normalize());
        }
        Set<Path> outputs = new HashSet<>();
        for (String output : List.of("--json", "--markdown")) {
            if (options.containsKey(output)) {
                Path path = normalized(required(options, output));
                if (inputs.contains(path) || !outputs.add(path)) {
                    throw new IllegalArgumentException(
                            "output must not overwrite an input or another output");
                }
            }
        }
    }

    private static boolean migrationVerificationRequested(String[] args) {
        Map<String, String> options = parse(args);
        return strictBoolean(
                options.getOrDefault("--verify-migration", "false"), "--verify-migration");
    }

    private static boolean strictBoolean(String value, String option) {
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new IllegalArgumentException(option + " must be true or false");
    }

    static String[] postgresApplicationArguments(String[] arguments) {
        String[] supplied = arguments == null ? new String[0] : arguments;
        String[] result = Arrays.copyOf(supplied, supplied.length + 2);
        result[supplied.length] = "--spring.main.web-application-type=none";
        result[supplied.length + 1] = "--portfolio.database.public.enabled=true";
        return result;
    }

    private static MigrationRuntime openPostgresRuntime(String[] applicationArguments) {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(
                PortfolioAgentApplication.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .logStartupInfo(false)
                .properties(Map.of("logging.level.root", "OFF"))
                .run(applicationArguments);
        try {
            PublicPortfolioRepository repository =
                    context.getBean(PublicPortfolioRepository.class);
            if (!(repository instanceof PostgresPublicPortfolioRepository postgresRepository)) {
                throw new IllegalStateException("PostgreSQL public repository is unavailable");
            }
            PostgresMigrationSnapshotSupplier supplier =
                    new PostgresRepositoryMigrationSnapshotSupplier(postgresRepository);
            return new MigrationRuntime() {
                @Override
                public PostgresMigrationSnapshotSupplier snapshotSupplier() {
                    return supplier;
                }

                @Override
                public void close() {
                    context.close();
                }
            };
        } catch (RuntimeException exception) {
            context.close();
            throw exception;
        }
    }

    private static Path normalized(String value) {
        return Path.of(value).toAbsolutePath().normalize();
    }

    private static Set<String> bundleFileNames() {
        return Set.of(
                "manifest.json", "portfolio.json", "presentation.json", "rag-documents.jsonl",
                "keyword-index.json", "vector-index.bin", "checksums.json");
    }

    @FunctionalInterface
    interface MigrationRuntimeFactory {
        MigrationRuntime open(String[] applicationArguments);
    }

    interface MigrationRuntime extends AutoCloseable {
        PostgresMigrationSnapshotSupplier snapshotSupplier();

        @Override
        void close();
    }
}
