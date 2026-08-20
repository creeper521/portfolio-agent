package com.portfolio.agent.evaluation.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.evaluation.application.EvalHarness;
import com.portfolio.agent.evaluation.application.EvalRunConfig;
import com.portfolio.agent.evaluation.application.EvalRunException;
import com.portfolio.agent.evaluation.dataset.EvalManifestLoader;
import com.portfolio.agent.evaluation.dataset.EvalSuiteLoader;
import com.portfolio.agent.evaluation.domain.EvalPolicy;
import com.portfolio.agent.evaluation.domain.EvalProviderAuthorization;
import com.portfolio.agent.evaluation.domain.EvalRunIdentity;
import com.portfolio.agent.evaluation.domain.EvalRunMode;
import com.portfolio.agent.evaluation.domain.EvalSuite;
import com.portfolio.agent.evaluation.domain.EvalVerdict;
import com.portfolio.agent.evaluation.reporting.EvalRunReport;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

public final class EvalCli {

    public static final int EXIT_PASS = 0;
    public static final int EXIT_FAIL = 1;
    public static final int EXIT_INVALID = 2;
    public static final int EXIT_INCOMPLETE = 3;

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        try {
            EvalCliArguments arguments = EvalCliArguments.parse(args);
            EvalCliBootstrap bootstrap = new EvalCliBootstrap(
                    arguments.hasFlag("authorize-real-provider"));
            return execute(arguments, bootstrap);
        } catch (IllegalArgumentException failure) {
            System.err.println("eval: " + failure.getMessage());
            return EXIT_INVALID;
        } catch (EvalRunException | IllegalStateException | IOException failure) {
            System.err.println("eval: " + failure.getMessage());
            return EXIT_INVALID;
        }
    }

    private static int execute(
            EvalCliArguments arguments,
            EvalCliBootstrap bootstrap) throws IOException {
        Path manifestPath = arguments.requiredPath("manifest");
        Path policyPath = arguments.requiredPath("policy");
        Path outputDir = arguments.requiredPath("output-dir");
        if (Files.exists(outputDir)) {
            throw new IllegalArgumentException("output-dir already exists");
        }

        EvalSuiteLoader suiteLoader = bootstrap.createSuiteLoader();
        EvalManifestLoader.EvalManifest manifest = new EvalManifestLoader()
                .load(manifestPath);
        java.util.List<com.portfolio.agent.evaluation.domain.EvalCase> allCases =
                new java.util.ArrayList<>();
        for (Path caseFile : manifest.getTrackedCaseFiles()) {
            allCases.addAll(suiteLoader.load(
                    Files.readAllBytes(caseFile)).getCases());
        }

        RuntimeContentSnapshot bundle = bootstrap.loadBundle();
        // Runtime expansion: every handwritten case plus every smoke case
        // deterministically generated from the tracked generation rules.
        java.util.List<com.portfolio.agent.evaluation.domain.EvalCase> expandedCases =
                expandWithGeneratedSmokeCases(allCases, manifest, bundle);
        EvalSuite suite = new EvalSuite(
                "1.0", manifest.getSuiteId(), manifest.getDatasetVersion(), expandedCases);
        EvalPolicy policy = bootstrap.createPolicyLoader().load(policyPath);

        EvalHarness harness = bootstrap.createHarness(bundle);
        EvalRunIdentity identity = identity(suite, bundle,
                manifest.getTrackedCaseFiles(), expandedCases, policyPath);
        EvalProviderAuthorization authorization =
                arguments.hasFlag("authorize-real-provider")
                        ? EvalProviderAuthorization.REAL_AUTHORIZED
                        : EvalProviderAuthorization.MOCK_ONLY;
        Optional<EvalVerdict> prerequisite = Optional.empty();

        EvalRunMode mode = switch (arguments.getCommand()) {
            case VALIDATE -> EvalRunMode.VALIDATE;
            case OFFLINE -> EvalRunMode.OFFLINE;
            case PROVIDER -> {
                prerequisite = offlinePrerequisite(arguments, identity);
                yield EvalRunMode.PROVIDER;
            }
            case PERIODIC -> {
                prerequisite = offlinePrerequisite(arguments, identity);
                yield EvalRunMode.PERIODIC;
            }
        };

        EvalRunConfig config = new EvalRunConfig(
                mode, identity, policy, Map.of(), Optional.empty(),
                authorization, prerequisite);

        EvalRunReport report = harness.run(suite, config);

        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("report.json"),
                bootstrap.createJsonWriter().write(report, expandedCases),
                StandardCharsets.UTF_8);
        Files.writeString(outputDir.resolve("report.md"),
                bootstrap.createMarkdownRenderer().render(report, false),
                StandardCharsets.UTF_8);

        return switch (report.getVerdict()) {
            case PASS -> EXIT_PASS;
            case FAIL -> EXIT_FAIL;
            case INCOMPLETE -> EXIT_INCOMPLETE;
        };
    }

    private static java.util.List<com.portfolio.agent.evaluation.domain.EvalCase>
            expandWithGeneratedSmokeCases(
                    java.util.List<com.portfolio.agent.evaluation.domain.EvalCase> handwritten,
                    EvalManifestLoader.EvalManifest manifest,
                    RuntimeContentSnapshot bundle) throws IOException {
        java.util.List<com.portfolio.agent.evaluation.domain.EvalCase> expanded =
                new java.util.ArrayList<>(handwritten);
        for (Path ruleFile : manifest.getGenerationRuleFiles()) {
            com.portfolio.agent.evaluation.dataset.GenerationRuleLoader.GenerationRule rule =
                    new com.portfolio.agent.evaluation.dataset.GenerationRuleLoader()
                            .load(ruleFile);
            expanded.addAll(new com.portfolio.agent.evaluation.dataset
                    .SmokeCaseGenerator().generate(bundle, rule));
        }
        // duplicate ids between handwritten and generated cases are a hard error
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (com.portfolio.agent.evaluation.domain.EvalCase evalCase : expanded) {
            if (!seen.add(evalCase.getId())) {
                throw new IllegalArgumentException(
                        "Duplicate case id after smoke expansion: " + evalCase.getId());
            }
        }
        return expanded;
    }

    private static Optional<EvalVerdict> offlinePrerequisite(
            EvalCliArguments arguments,
            EvalRunIdentity expectedIdentity) throws IOException {
        Path offlineReport = arguments.requiredPath("offline-report");
        String json = Files.readString(offlineReport, StandardCharsets.UTF_8);
        JsonNode root;
        try {
            root = new ObjectMapper().readTree(json);
        } catch (IOException failure) {
            throw new IllegalArgumentException("offline report is not valid JSON", failure);
        }
        if (!"OFFLINE".equals(root.path("mode").asText())
                || !"PASS".equals(root.path("verdict").asText())) {
            throw new IllegalArgumentException(
                    "offline report must carry mode=OFFLINE and verdict=PASS");
        }
        JsonNode identity = root.path("identity");
        if (!expectedIdentity.getDatasetVersion().equals(identity.path("datasetVersion").asText())
                || !expectedIdentity.getDatasetHash().equals(identity.path("datasetHash").asText())
                || !expectedIdentity.getBundleVersion().equals(identity.path("bundleVersion").asText())
                || !expectedIdentity.getBundleHash().equals(identity.path("bundleHash").asText())
                || !expectedIdentity.getRetrievalPolicyHash().equals(
                        identity.path("retrievalPolicyHash").asText())) {
            throw new IllegalArgumentException(
                    "offline report identity does not match manifest, bundle, or policy");
        }
        return Optional.of(EvalVerdict.PASS);
    }

    private static EvalRunIdentity identity(
            EvalSuite suite,
            RuntimeContentSnapshot bundle,
            java.util.List<Path> handwrittenCaseFiles,
            java.util.List<com.portfolio.agent.evaluation.domain.EvalCase> expandedCases,
            Path policyPath) {
        // real dataset identity: SHA-256 over the handwritten case file bytes
        // plus the canonical JSON of the expanded (generated) smoke cases
        String datasetHash = new com.portfolio.agent.evaluation.dataset
                .EvalDatasetHasher(new com.fasterxml.jackson.databind.ObjectMapper())
                .hash(handwrittenCaseFiles, expandedCases);
        String policyHash = sha256Hex(readPolicyBytes(policyPath));
        return EvalRunIdentity.create(
                "cli", suite.getDatasetVersion(), datasetHash,
                bundle.getContentVersion(), bundle.getRuntimeBundleHash(),
                EvalRunIdentity.NOT_APPLICABLE, policyHash,
                EvalRunIdentity.NOT_APPLICABLE, EvalRunIdentity.NOT_APPLICABLE,
                EvalRunIdentity.NOT_APPLICABLE, EvalRunIdentity.NOT_APPLICABLE,
                EvalRunIdentity.NOT_APPLICABLE, EvalRunIdentity.NOT_APPLICABLE,
                EvalRunIdentity.NOT_APPLICABLE);
    }

    private static byte[] readPolicyBytes(Path policyPath) {
        try {
            return Files.readAllBytes(policyPath);
        } catch (IOException failure) {
            throw new IllegalArgumentException(
                    "unable to read policy file: " + policyPath, failure);
        }
    }

    private static String sha256Hex(byte[] value) {
        try {
            java.security.MessageDigest digest =
                    java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(value));
        } catch (java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
