package com.portfolio.agent.turn.infrastructure.model;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.ModelOutputDiagnostics;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.structured.StructuredContractRef;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputContractRegistry;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputValidationException;
import com.portfolio.agent.infrastructure.model.structured.StructurallyValidatedOutput;
import com.portfolio.agent.turn.capability.general.GeneralDraftCodec;
import com.portfolio.agent.turn.capability.general.GeneralDraftValidationException;
import com.portfolio.agent.turn.capability.general.GeneralDraftValidator;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeRequest;
import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test-only v3/v4 replay authority. It never participates in a production bean,
 * HTTP route or runtime fallback; the CLI invokes the property-driven method in
 * one isolated Maven test process and receives only closed aggregate metadata.
 */
class GeneralProviderDraftDualReplayTest {
    private static final ObjectMapper JSON = new ObjectMapper(
            JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final StructuredContractRef PROVIDER_V3 =
            new StructuredContractRef(
                    ModelOperation.GENERAL_KNOWLEDGE,
                    "general.provider-draft.v3");
    private static final StructuredContractRef PROVIDER_V4 =
            new StructuredContractRef(
                    ModelOperation.GENERAL_KNOWLEDGE,
                    "general.provider-draft.v4");
    private static final StructuredContractRef CANONICAL_V2 =
            new StructuredContractRef(
                    ModelOperation.GENERAL_KNOWLEDGE,
                    "general.draft.v2");
    private static final StructuredContractRef CANONICAL_V3 =
            new StructuredContractRef(
                    ModelOperation.GENERAL_KNOWLEDGE,
                    "general.draft.v3");
    private static final Set<String> ACCEPTED = Set.of(
            "EXACT", "NORMALIZED", "DEGRADED");
    private static final Set<String> LATENCY_BUCKETS = Set.of(
            "LT_100_MS", "FROM_100_TO_499_MS",
            "FROM_500_TO_1999_MS", "FROM_2000_TO_9999_MS",
            "GTE_10000_MS");
    private static final Set<String> METADATA_FIELDS = Set.of(
            "schemaVersion", "artifactId", "caseId", "depth",
            "createdAtUtc", "expiresAtUtc", "operatorIdentitySha256",
            "provider", "model", "selectionVersion", "providerContract",
            "compilerProfile", "status", "httpClass", "latencyBucket",
            "latencyMs", "attemptCount", "captureSource");
    private static final String SEMANTIC_FIXTURE_AUTHORIZATION =
            "AUTHORIZED_POST_CANONICAL_VALIDATOR_FIXTURE_ONLY";
    private static final String LEGACY_EXECUTABLE_SHA256 =
            "39481b3c00938b8df1d141f3f084e0bfb306a341aa0152b3706ff3d7af64bd7e";
    private static final Map<String, String> LEGACY_SOURCE_BLOBS = Map.of(
            "GeneralProviderDraftCompiler.java.snapshot",
            LegacyGeneralV3Baseline.COMPILER_BLOB,
            "GeneralDraftCodec.java.snapshot",
            LegacyGeneralV3Baseline.CODEC_BLOB,
            "GeneralDraftValidator.java.snapshot",
            LegacyGeneralV3Baseline.VALIDATOR_BLOB,
            "GeneralDraftRules.java.snapshot",
            LegacyGeneralV3Baseline.RULES_BLOB);

    private final StructuredOutputContractRegistry contracts =
            StructuredOutputContractRegistry.standard();

    @Test
    void legacyBaselineIdentityAndDepthRulesStayFrozen() throws Exception {
        LegacyGeneralV3Baseline.verifyRetainedSchemaIdentity();
        verifyLegacySourceSnapshots();
        assertThat(sha256(Path.of(
                "src/test/java/com/portfolio/agent/turn/infrastructure/model/"
                        + "LegacyGeneralV3Baseline.java")))
                .isEqualTo(LEGACY_EXECUTABLE_SHA256);
        assertThat(LegacyGeneralV3Baseline.BASELINE_COMMIT)
                .isEqualTo("b5cf941");
        assertThat(List.of(
                LegacyGeneralV3Baseline.COMPILER_BLOB,
                LegacyGeneralV3Baseline.CODEC_BLOB,
                LegacyGeneralV3Baseline.VALIDATOR_BLOB,
                LegacyGeneralV3Baseline.RULES_BLOB))
                .allMatch(value -> value.matches("^[0-9a-f]{40}$"));

        JsonNode legacy = JSON.readTree("""
                {
                  "kind":"EXPLANATION",
                  "depth":"STANDARD",
                  "definitionSentences":["这是定义","这是用法"],
                  "mechanismSentences":["这是机制","这是边界"],
                  "caveats":[]
                }
                """);
        assertThat(replayV3(
                legacy,
                request("依赖注入", UserGoalProposal.Depth.STANDARD))
                .outcome()).isEqualTo("EXACT");
        assertThat(replayV3(
                legacy,
                request("依赖注入", UserGoalProposal.Depth.DETAILED))
                .outcome()).isEqualTo("INCOMPLETE");
    }

    @Test
    void legacySnapshotHashIsStableAcrossCheckoutLineEndings() throws Exception {
        byte[] lf = "first\nsecond\n".getBytes(StandardCharsets.UTF_8);
        byte[] crlf = "first\r\nsecond\r\n".getBytes(StandardCharsets.UTF_8);

        assertThat(gitBlobSha1(crlf)).isEqualTo(gitBlobSha1(lf));
    }

    @Test
    void legacyGoldenBehaviorMatrixFreezesCompilerCodecValidatorAndPunctuation()
            throws Exception {
        for (Map.Entry<UserGoalProposal.Depth, Integer> entry : Map.of(
                UserGoalProposal.Depth.CONCISE, 1,
                UserGoalProposal.Depth.STANDARD, 2,
                UserGoalProposal.Depth.DETAILED, 4).entrySet()) {
            GeneralKnowledgeRequest request = request("依赖注入", entry.getKey());
            JsonNode canonical = LegacyGeneralV3Baseline.compile(
                    JSON, legacyDraft(entry.getKey(), entry.getValue()), request);
            assertThat(canonical.path("statements").get(0).path("text")
                    .textValue()).endsWith("。");
            LegacyGeneralV3Baseline.decodeAndValidate(canonical, request);
        }

        GeneralKnowledgeRequest standard = request(
                "依赖注入", UserGoalProposal.Depth.STANDARD);
        assertThatThrownBy(() -> LegacyGeneralV3Baseline.compile(
                JSON, legacyDraft(UserGoalProposal.Depth.CONCISE, 1), standard))
                .isInstanceOf(IllegalArgumentException.class);
        ObjectNode semicolon = (ObjectNode) legacyDraft(
                UserGoalProposal.Depth.STANDARD, 2);
        ((ArrayNode) semicolon.get("definitionSentences"))
                .set(0, JSON.getNodeFactory().textNode("这是定义；包含分号"));
        assertThatThrownBy(() -> LegacyGeneralV3Baseline.compile(
                JSON, semicolon, standard))
                .isInstanceOf(IllegalArgumentException.class);

        ObjectNode unknownCanonical = (ObjectNode) LegacyGeneralV3Baseline.compile(
                JSON, legacyDraft(UserGoalProposal.Depth.STANDARD, 2), standard);
        unknownCanonical.put("unknown", true);
        assertThatThrownBy(() -> LegacyGeneralV3Baseline.decodeAndValidate(
                unknownCanonical, standard))
                .isInstanceOf(IllegalArgumentException.class);

        ObjectNode wrongCoverage = (ObjectNode) LegacyGeneralV3Baseline.compile(
                JSON, legacyDraft(UserGoalProposal.Depth.STANDARD, 2), standard);
        ((ObjectNode) wrongCoverage.withArray("statements").get(0))
                .putArray("aspects").add("DEFINITION");
        assertThatThrownBy(() -> LegacyGeneralV3Baseline.decodeAndValidate(
                wrongCoverage, standard))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void legacyAndCandidateOutcomesRemainIndependent() throws Exception {
        GeneralKnowledgeRequest request = request(
                "依赖注入", UserGoalProposal.Depth.STANDARD);
        JsonNode candidate = JSON.readTree("""
                {
                  "definition":"依赖注入由容器提供依赖",
                  "mechanism":["对象声明依赖","容器解析并注入依赖"],
                  "unknown":{"never":"persist"}
                }
                """);

        assertThat(replayV3(candidate, request).outcome())
                .isEqualTo("INCOMPLETE");
        ReplayOutcome v4 = replayV4(candidate, request);
        assertThat(v4.outcome()).isEqualTo("DEGRADED");
        assertThat(v4.normalizationRuleCounts())
                .containsEntry("UNKNOWN_FIELD_COUNT", 1)
                .containsEntry("MISSING_CAVEATS_AS_EMPTY", 1)
                .doesNotContainKey("unknown");
    }

    @Test
    void requiredToolEnvelopeRejectsEveryCarrierAmbiguity() throws Exception {
        ObjectNode valid = envelope(JSON.createObjectNode()
                .put("definition", "definition")
                .put("mechanism", "mechanism"));
        assertThat(extractArguments(JSON.writeValueAsString(valid)))
                .isNotNull();

        List<ObjectNode> invalid = new ArrayList<>();
        ObjectNode badFinish = valid.deepCopy();
        ((ObjectNode) badFinish.withArray("choices").get(0))
                .put("finish_reason", "tool_calls");
        invalid.add(badFinish);
        ObjectNode refusal = valid.deepCopy();
        message(refusal).put("refusal", "declined");
        invalid.add(refusal);
        ObjectNode content = valid.deepCopy();
        message(content).put("content", "mixed carrier");
        invalid.add(content);
        ObjectNode multiple = valid.deepCopy();
        toolCalls(multiple).add(toolCalls(multiple).get(0).deepCopy());
        invalid.add(multiple);
        ObjectNode wrongType = valid.deepCopy();
        firstToolCall(wrongType).put("type", "custom");
        invalid.add(wrongType);
        ObjectNode wrongName = valid.deepCopy();
        ((ObjectNode) firstToolCall(wrongName).get("function"))
                .put("name", "other_tool");
        invalid.add(wrongName);

        for (ObjectNode value : invalid) {
            assertThatThrownBy(() -> extractArguments(
                    JSON.writeValueAsString(value)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("closed envelope rejection");
        }
    }

    @Test
    void candidateRejectsMissingCoreAndInvalidCanonicalShape() throws Exception {
        GeneralKnowledgeRequest request = request(
                "依赖注入", UserGoalProposal.Depth.CONCISE);
        JsonNode missingDefinition = JSON.readTree("""
                {"mechanism":["这是机制"]}
                """);
        assertThat(replayV4(missingDefinition, request).outcome())
                .isEqualTo("INCOMPLETE");

        ObjectNode invalidCanonical = JSON.createObjectNode();
        invalidCanonical.put("topic", "依赖注入");
        invalidCanonical.putArray("statements");
        invalidCanonical.putArray("caveats");
        assertThatThrownBy(() -> validateAtStage(
                CANONICAL_V3,
                invalidCanonical,
                StructuredOutputValidationException.Stage.CANONICAL_SCHEMA))
                .isInstanceOf(StructuredOutputValidationException.class);
    }

    @Test
    void replaysExternalArtifactBatch() throws Exception {
        String rawRootValue = System.getProperty("dualReplay.rawRoot", "");
        String outputValue = System.getProperty("dualReplay.output", "");
        String corpusValue = System.getProperty("dualReplay.corpus", "");
        String captureSourceValue = System.getProperty(
                "dualReplay.captureSource", "REAL_PROVIDER");
        String fixtureCaseId = System.getProperty(
                "dualReplay.semanticFixtureCaseId", "");
        String fixtureDepth = System.getProperty(
                "dualReplay.semanticFixtureDepth", "");
        String fixtureCaseKey = fixtureCaseId.isBlank() && fixtureDepth.isBlank()
                ? ""
                : fixtureCaseId + "|" + fixtureDepth;
        String fixtureAuthorization = System.getProperty(
                "dualReplay.semanticFixtureAuthorization", "");
        if (rawRootValue.isBlank() && outputValue.isBlank()
                && corpusValue.isBlank()) {
            return;
        }
        if (rawRootValue.isBlank() || outputValue.isBlank()
                || corpusValue.isBlank()) {
            throw new IllegalArgumentException("dual replay properties are incomplete");
        }
        if ((fixtureCaseId.isBlank() != fixtureDepth.isBlank())
                || (!fixtureCaseKey.isBlank()
                    && !SEMANTIC_FIXTURE_AUTHORIZATION.equals(
                        fixtureAuthorization))
                || (fixtureCaseKey.isBlank()
                    && !fixtureAuthorization.isBlank())) {
            throw new IllegalArgumentException(
                    "semantic replay fixture authorization is invalid");
        }

        Path rawRoot = Path.of(rawRootValue).toRealPath();
        Path output = Path.of(outputValue).toAbsolutePath().normalize();
        Path corpus = Path.of(corpusValue).toRealPath();
        Map<String, String> topics = readTopics(corpus);
        List<ReplaySample> samples = new ArrayList<>();
        int semanticFixtureExecutions = 0;
        try (Stream<Path> children = Files.list(rawRoot)) {
            for (Path artifact : children.sorted().toList()) {
                if (!Files.isDirectory(artifact) || Files.isSymbolicLink(artifact)) {
                    continue;
                }
                Path realArtifact = artifact.toRealPath();
                if (!realArtifact.startsWith(rawRoot)) {
                    throw new IllegalArgumentException(
                            "dual replay artifact escaped its root");
                }
                Path metadataPath = validatedArtifactFile(
                        rawRoot, realArtifact, "metadata.json", 32_768L);
                JsonNode metadata = strictObject(Files.readString(
                        metadataPath, StandardCharsets.UTF_8));
                requireExactFields(metadata, METADATA_FIELDS);
                if (!"qwen-general-lab-artifact.v2".equals(
                        closedText(metadata, "schemaVersion", 64))
                        || !realArtifact.getFileName().toString().equals(
                                closedText(metadata, "artifactId", 160))
                        || !"QWEN".equals(closedText(metadata, "provider", 16))
                        || !"qwen3.7-flash".equals(
                                closedText(metadata, "model", 64))
                        || !"qwen-3-7-flash-v8".equals(
                                closedText(metadata, "selectionVersion", 64))
                        || !"general.provider-draft.v4".equals(
                                closedText(metadata, "providerContract", 64))
                        || !"general-provider-draft-compiler.v4".equals(
                                closedText(metadata, "compilerProfile", 96))
                        || !LATENCY_BUCKETS.contains(
                                closedText(metadata, "latencyBucket", 32))
                        || !metadata.path("latencyMs").isIntegralNumber()
                        || metadata.path("latencyMs").longValue() < 0
                        || metadata.path("latencyMs").longValue() > 120_000
                        || !metadata.path("attemptCount").isIntegralNumber()
                        || metadata.path("attemptCount").intValue() < 1
                        || metadata.path("attemptCount").intValue() > 2
                        || !captureSourceValue.equals(
                                closedText(metadata, "captureSource", 32))) {
                    throw new IllegalArgumentException(
                            "closed metadata is invalid");
                }
                String caseId = closedText(metadata, "caseId", 48);
                String depthValue = closedText(metadata, "depth", 16);
                UserGoalProposal.Depth depth = UserGoalProposal.Depth.valueOf(
                        depthValue);
                String topic = topics.get(caseId);
                if (topic == null) {
                    throw new IllegalArgumentException(
                            "dual replay case is outside the frozen corpus");
                }
                String status = closedText(metadata, "status", 32);
                String httpClass = closedText(metadata, "httpClass", 32);
                if (!closedTransportPair(status, httpClass)) {
                    throw new IllegalArgumentException(
                            "closed metadata is invalid");
                }
                if (!"CAPTURED".equals(status)) {
                    samples.add(new ReplaySample(
                            caseId,
                            depthValue,
                            ReplayOutcome.notApplicable(httpClass),
                            ReplayOutcome.notApplicable(httpClass)));
                    continue;
                }
                Path responsePath = validatedArtifactFile(
                        rawRoot, realArtifact, "response.raw.json", 131_072L);
                JsonNode draft = extractArguments(Files.readString(
                        responsePath, StandardCharsets.UTF_8));
                GeneralKnowledgeRequest request = request(topic, depth);
                boolean injectSemanticFixture =
                        (caseId + "|" + depthValue).equals(fixtureCaseKey);
                if (injectSemanticFixture) {
                    semanticFixtureExecutions++;
                }
                samples.add(new ReplaySample(
                        caseId, depthValue,
                        replayV3(draft, request),
                        replayV4(draft, request, injectSemanticFixture)));
            }
        }
        if ((!fixtureCaseKey.isBlank() && semanticFixtureExecutions != 1)
                || (fixtureCaseKey.isBlank() && semanticFixtureExecutions != 0)) {
            throw new IllegalArgumentException(
                    "semantic replay fixture case is invalid");
        }
        samples.sort(Comparator.comparing(ReplaySample::caseId)
                .thenComparing(ReplaySample::depth));
        if (samples.isEmpty() || samples.size() > 300) {
            throw new IllegalArgumentException(
                    "dual replay sample count is invalid");
        }

        ObjectNode aggregate = JSON.createObjectNode();
        aggregate.put("schemaVersion", "qwen-general-dual-replay.v1");
        aggregate.put("corpusVersion", "qwen-general-explanation-corpus.v1");
        aggregate.put("fixtureMode", fixtureCaseKey.isBlank()
                ? "NONE"
                : "TEST_ONLY_POST_CANONICAL_SEMANTIC_REJECT");
        if (fixtureCaseKey.isBlank()) {
            aggregate.putNull("fixtureCaseKey");
        } else {
            aggregate.put("fixtureCaseKey", fixtureCaseKey);
        }
        ArrayNode values = aggregate.putArray("samples");
        for (ReplaySample sample : samples) {
            ObjectNode value = values.addObject();
            value.put("caseId", sample.caseId());
            value.put("depth", sample.depth());
            value.set("v3", outcomeNode(sample.v3(), false));
            value.set("v4", outcomeNode(sample.v4(), true));
        }
        Path parent = output.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(output, JSON.writerWithDefaultPrettyPrinter()
                .writeValueAsString(aggregate), StandardCharsets.UTF_8);
    }

    private ReplayOutcome replayV3(
            JsonNode draft, GeneralKnowledgeRequest request) {
        try {
            JsonNode validated = validateAtStage(
                    PROVIDER_V3, draft,
                    StructuredOutputValidationException.Stage
                            .PROVIDER_DRAFT_SCHEMA).jsonTree();
            JsonNode canonical = LegacyGeneralV3Baseline.compile(
                    JSON, validated, request);
            validateAtStage(
                    CANONICAL_V2, canonical,
                    StructuredOutputValidationException.Stage.CANONICAL_SCHEMA);
            LegacyGeneralV3Baseline.decodeAndValidate(canonical, request);
            return ReplayOutcome.exact();
        } catch (StructuredOutputValidationException rejected) {
            return ReplayOutcome.incomplete(
                    layerFor(rejected, "PROVIDER_DRAFT_SCHEMA"),
                    rejected.getDiagnosticReason());
        } catch (RuntimeException rejected) {
            return ReplayOutcome.incomplete(
                    "DETERMINISTIC_COMPILER", "CLOSED_REJECTION");
        }
    }

    private ReplayOutcome replayV4(
            JsonNode draft, GeneralKnowledgeRequest request) {
        return replayV4(draft, request, false);
    }

    private ReplayOutcome replayV4(
            JsonNode draft,
            GeneralKnowledgeRequest request,
            boolean injectSemanticFixture) {
        List<DiagnosticEvent> events = new ArrayList<>();
        try {
            StructurallyValidatedOutput provider = validateAtStage(
                    PROVIDER_V4, draft,
                    StructuredOutputValidationException.Stage
                            .PROVIDER_DRAFT_SCHEMA);
            JsonNode canonical = new GeneralProviderDraftCompiler(
                    request, new ModelOutputDiagnostics(events::add))
                    .compile(provider.jsonTree());
            if (injectSemanticFixture) {
                ((ObjectNode) canonical).put(
                        "topic", "测试专用语义偏移");
            }
            StructurallyValidatedOutput application = validateAtStage(
                    CANONICAL_V3, canonical,
                    StructuredOutputValidationException.Stage.CANONICAL_SCHEMA);
            GeneralDraftCodec.Draft decoded = new GeneralDraftCodec(JSON)
                    .decode(application);
            new GeneralDraftValidator().validate(request, decoded);
            return acceptedOutcome(events);
        } catch (StructuredOutputValidationException rejected) {
            return ReplayOutcome.incomplete(
                    layerFor(rejected, "PROVIDER_DRAFT_SCHEMA"),
                    rejected.getDiagnosticReason());
        } catch (GeneralDraftValidationException rejected) {
            return ReplayOutcome.incomplete(
                    "SEMANTIC", rejected.getReason().name());
        } catch (RuntimeException rejected) {
            return ReplayOutcome.incomplete(
                    "CLOSED_PIPELINE", "CLOSED_REJECTION");
        }
    }

    private ReplayOutcome acceptedOutcome(List<DiagnosticEvent> events) {
        String level = "EXACT";
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (DiagnosticEvent event : events) {
            if (!"provider.output.admitted".equals(event.getName())) {
                continue;
            }
            Object eventLevel = event.getFields().get("admission.level");
            if ("DEGRADED".equals(eventLevel)) {
                level = "DEGRADED";
            } else if ("NORMALIZED".equals(eventLevel)
                    && "EXACT".equals(level)) {
                level = "NORMALIZED";
            }
            Object rule = event.getFields().get("normalization.rule");
            Object count = event.getFields().get("normalization.count");
            if (rule instanceof String ruleName && count instanceof Number number) {
                counts.merge(ruleName, number.intValue(), Integer::sum);
            }
        }
        return new ReplayOutcome(
                level, "ACCEPTED", "ACCEPTED", Map.copyOf(counts));
    }

    private Map<String, String> readTopics(Path corpus) throws Exception {
        JsonNode root = strictObject(Files.readString(
                corpus, StandardCharsets.UTF_8));
        Map<String, String> topics = new LinkedHashMap<>();
        for (JsonNode value : root.path("cases")) {
            String caseId = closedText(value, "caseId", 48);
            String topic = closedText(value, "topic", 256);
            if (topics.putIfAbsent(caseId, topic) != null) {
                throw new IllegalArgumentException(
                        "duplicate case in frozen corpus");
            }
        }
        return Map.copyOf(topics);
    }

    private JsonNode legacyDraft(UserGoalProposal.Depth depth, int perRole) {
        ObjectNode draft = JSON.createObjectNode();
        draft.put("kind", "EXPLANATION");
        draft.put("depth", depth.name());
        ArrayNode definitions = draft.putArray("definitionSentences");
        ArrayNode mechanisms = draft.putArray("mechanismSentences");
        for (int index = 0; index < perRole; index++) {
            definitions.add("这是定义内容" + toChineseOrdinal(index));
            mechanisms.add("这是机制内容" + toChineseOrdinal(index));
        }
        draft.putArray("caveats");
        return draft;
    }

    private String toChineseOrdinal(int index) {
        return switch (index) {
            case 0 -> "一";
            case 1 -> "二";
            case 2 -> "三";
            case 3 -> "四";
            default -> throw new IllegalArgumentException("ordinal is outside fixture");
        };
    }

    private void verifyLegacySourceSnapshots() throws Exception {
        for (Map.Entry<String, String> entry : LEGACY_SOURCE_BLOBS.entrySet()) {
            String resource = "/provider-diagnostic-lab/legacy-v3/b5cf941/"
                    + entry.getKey();
            try (java.io.InputStream stream = getClass().getResourceAsStream(resource)) {
                assertThat(stream).as(resource).isNotNull();
                assertThat(gitBlobSha1(stream.readAllBytes()))
                        .as(resource).isEqualTo(entry.getValue());
            }
        }
    }

    private String gitBlobSha1(byte[] bytes) throws Exception {
        byte[] gitTextBytes = normalizeGitText(bytes);
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        digest.update(("blob " + gitTextBytes.length + "\0")
                .getBytes(StandardCharsets.UTF_8));
        return hex(digest.digest(gitTextBytes));
    }

    private String sha256(Path path) throws Exception {
        return hex(MessageDigest.getInstance("SHA-256")
                .digest(normalizeGitText(Files.readAllBytes(path))));
    }

    private byte[] normalizeGitText(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            value.append(String.format("%02x", current & 0xff));
        }
        return value.toString();
    }

    private JsonNode extractArguments(String rawEnvelope) throws Exception {
        JsonNode root = strictObject(rawEnvelope);
        if (!"qwen3.7-flash".equals(root.path("model").asText())) {
            throw new IllegalArgumentException("closed envelope rejection");
        }
        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.size() != 1) {
            throw new IllegalArgumentException("closed envelope rejection");
        }
        JsonNode choice = choices.get(0);
        if (!"stop".equals(choice.path("finish_reason").asText())) {
            throw new IllegalArgumentException("closed envelope rejection");
        }
        JsonNode message = choice.get("message");
        if (message == null || !message.isObject()) {
            throw new IllegalArgumentException("closed envelope rejection");
        }
        JsonNode refusal = message.get("refusal");
        if (refusal != null && !refusal.isNull()) {
            throw new IllegalArgumentException("closed envelope rejection");
        }
        JsonNode content = message.get("content");
        if (content != null && !content.isNull()
                && (!content.isTextual() || !content.textValue().isBlank())) {
            throw new IllegalArgumentException("closed envelope rejection");
        }
        JsonNode calls = message.get("tool_calls");
        if (calls == null || !calls.isArray() || calls.size() != 1) {
            throw new IllegalArgumentException("closed envelope rejection");
        }
        JsonNode toolCall = calls.get(0);
        if (!"function".equals(toolCall.path("type").asText())) {
            throw new IllegalArgumentException("closed envelope rejection");
        }
        JsonNode function = toolCall.get("function");
        if (function == null || !function.isObject()
                || !"emit_general_provider_draft_v4".equals(
                        function.path("name").asText())
                || !function.path("arguments").isTextual()
                || function.path("arguments").textValue().isBlank()) {
            throw new IllegalArgumentException("closed envelope rejection");
        }
        return strictObject(function.path("arguments").textValue());
    }

    private ObjectNode envelope(JsonNode arguments) throws Exception {
        ObjectNode root = JSON.createObjectNode();
        root.put("model", "qwen3.7-flash");
        ObjectNode choice = root.putArray("choices").addObject();
        choice.put("finish_reason", "stop");
        ObjectNode message = choice.putObject("message");
        message.putNull("content");
        message.putNull("refusal");
        ObjectNode call = message.putArray("tool_calls").addObject();
        call.put("type", "function");
        ObjectNode function = call.putObject("function");
        function.put("name", "emit_general_provider_draft_v4");
        function.put("arguments", JSON.writeValueAsString(arguments));
        return root;
    }

    private ObjectNode message(ObjectNode envelope) {
        ObjectNode choice = (ObjectNode) envelope.withArray("choices").get(0);
        return (ObjectNode) choice.get("message");
    }

    private ArrayNode toolCalls(ObjectNode envelope) {
        return (ArrayNode) message(envelope).get("tool_calls");
    }

    private ObjectNode firstToolCall(ObjectNode envelope) {
        return (ObjectNode) toolCalls(envelope).get(0);
    }

    private Path validatedArtifactFile(
            Path rawRoot,
            Path artifact,
            String fileName,
            long maximumBytes) throws Exception {
        Path candidate = artifact.resolve(fileName).toAbsolutePath().normalize();
        if (!candidate.startsWith(artifact)
                || Files.isSymbolicLink(candidate)
                || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(
                    "dual replay artifact file is invalid");
        }
        Path real = candidate.toRealPath();
        if (!real.startsWith(rawRoot) || !real.startsWith(artifact)
                || Files.size(real) > maximumBytes) {
            throw new IllegalArgumentException(
                    "dual replay artifact file is invalid");
        }
        return real;
    }

    private void requireExactFields(JsonNode node, Set<String> expected) {
        Set<String> actual = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException("closed metadata is invalid");
        }
    }

    private JsonNode strictObject(String value) throws Exception {
        JsonNode parsed = JSON.readTree(value);
        if (parsed == null || !parsed.isObject()) {
            throw new IllegalArgumentException("strict object required");
        }
        return parsed;
    }

    private String closedText(JsonNode node, String field, int maximum) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()
                || value.textValue().isBlank()
                || value.textValue().length() > maximum) {
            throw new IllegalArgumentException("closed metadata is invalid");
        }
        return value.textValue();
    }

    private boolean closedTransportPair(String status, String httpClass) {
        return switch (status) {
            case "CAPTURED" -> "SUCCESS".equals(httpClass);
            case "RATE_LIMITED" -> "RATE_LIMITED".equals(httpClass);
            case "SERVER_ERROR" -> "SERVER_ERROR".equals(httpClass);
            case "CLIENT_ERROR" -> "CLIENT_ERROR".equals(httpClass);
            case "TRANSPORT_FAILED" ->
                    "TRANSPORT_UNAVAILABLE".equals(httpClass);
            case "RESPONSE_REJECTED" -> "SUCCESS".equals(httpClass);
            default -> false;
        };
    }

    private GeneralKnowledgeRequest request(
            String topic, UserGoalProposal.Depth depth) {
        Clock clock = Clock.fixed(Instant.parse(
                "2026-08-28T00:00:00Z"), java.time.ZoneOffset.UTC);
        return GeneralKnowledgeRequest.explanation(
                topic, depth, GeneralKnowledgeRequest.Audience.GUEST,
                "public-1", TurnDeadline.after(Duration.ofSeconds(30), clock));
    }

    private String layerFor(
            StructuredOutputValidationException failure,
            String fallback) {
        return switch (failure.getStage()) {
            case PROVIDER_DRAFT_SCHEMA -> "PROVIDER_DRAFT_SCHEMA";
            case DETERMINISTIC_COMPILER -> "DETERMINISTIC_COMPILER";
            case CANONICAL_SCHEMA -> "CANONICAL_SCHEMA";
            case UNCLASSIFIED_SCHEMA -> fallback;
        };
    }

    private StructurallyValidatedOutput validateAtStage(
            StructuredContractRef contract,
            JsonNode tree,
            StructuredOutputValidationException.Stage stage) {
        try {
            return contracts.validateTree(contract, tree);
        } catch (StructuredOutputValidationException failure) {
            throw failure.atStage(stage);
        }
    }

    private ObjectNode outcomeNode(
            ReplayOutcome outcome, boolean includeRules) {
        ObjectNode node = JSON.createObjectNode();
        node.put("outcome", outcome.outcome());
        node.put("layer", outcome.layer());
        node.put("reason", outcome.reason());
        if (includeRules) {
            ObjectNode rules = node.putObject("normalizationRuleCounts");
            outcome.normalizationRuleCounts().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> rules.put(entry.getKey(), entry.getValue()));
        }
        return node;
    }

    private record ReplaySample(
            String caseId,
            String depth,
            ReplayOutcome v3,
            ReplayOutcome v4) { }

    private record ReplayOutcome(
            String outcome,
            String layer,
            String reason,
            Map<String, Integer> normalizationRuleCounts) {
        private ReplayOutcome {
            if (!ACCEPTED.contains(outcome)
                    && !"INCOMPLETE".equals(outcome)
                    && !"NOT_APPLICABLE".equals(outcome)) {
                throw new IllegalArgumentException("closed outcome is invalid");
            }
            normalizationRuleCounts = Map.copyOf(normalizationRuleCounts);
        }

        private static ReplayOutcome exact() {
            return new ReplayOutcome(
                    "EXACT", "ACCEPTED", "ACCEPTED", Map.of());
        }

        private static ReplayOutcome incomplete(String layer, String reason) {
            return new ReplayOutcome(
                    "INCOMPLETE", layer, reason, Map.of());
        }

        private static ReplayOutcome notApplicable(String reason) {
            return new ReplayOutcome(
                    "NOT_APPLICABLE", "TRANSPORT", reason, Map.of());
        }
    }
}
