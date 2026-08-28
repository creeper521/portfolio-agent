package com.portfolio.agent.infrastructure.model;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portfolio.agent.infrastructure.model.configuration.ApprovedModelExecutionProfile;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderProtocolProfile;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderDescriptor;
import com.portfolio.agent.infrastructure.model.provider.ModelRef;
import com.portfolio.agent.infrastructure.model.structured.OperationBinding;
import com.portfolio.agent.infrastructure.model.structured.StructuredContractRef;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputGateway;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputContractRegistry;
import com.portfolio.agent.infrastructure.model.structured.StructurallyValidatedOutput;
import com.portfolio.agent.turn.capability.general.GeneralDraftCodec;
import com.portfolio.agent.turn.capability.general.GeneralDraftValidator;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeRequest;
import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.turn.infrastructure.model.GeneralProviderDraftCompiler;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Test-only machine guard producer for the Qwen General certification chain. */
class QwenGeneralCertificationGuardTest {
    private static final ObjectMapper JSON = new ObjectMapper(
            JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final StructuredContractRef PROVIDER_V4 =
            new StructuredContractRef(
                    ModelOperation.GENERAL_KNOWLEDGE,
                    "general.provider-draft.v4");
    private static final StructuredContractRef CANONICAL_V3 =
            new StructuredContractRef(
                    ModelOperation.GENERAL_KNOWLEDGE,
                    "general.draft.v3");

    @Test
    void emitsExecutedZeroToleranceGuardMatrix() throws Exception {
        StructuredOutputContractRegistry contracts =
                StructuredOutputContractRegistry.standard();
        ApprovedModelExecutionProfile profile =
                ApprovedModelExecutionProfile.resolve(
                        ApprovedModelExecutionProfile.QWEN_PROFILE, contracts);

        GateCounter providerModelRef = new GateCounter();
        providerModelRef.recordRejected(rejectsProviderModelRef(profile));
        GateCounter selectionVersion = new GateCounter();
        selectionVersion.recordRejected(rejectsSelectionVersion(profile));
        GateCounter operationBinding = new GateCounter();
        operationBinding.recordRejected(rejectsOperationBinding(profile));
        GateCounter protocolProfile = new GateCounter();
        protocolProfile.recordRejected(rejectsProtocolProfile(profile));
        GateCounter responseModelIdentity = new GateCounter();
        responseModelIdentity.recordRejected(
                rejectsResponseModelIdentity(contracts, profile));
        GateCounter requiredToolEnvelope = new GateCounter();
        requiredToolEnvelope.recordRejected(rejectsEnvelope(
                contracts, profile, envelope(
                        "executeInvestment", validDraft()),
                StructuredModelFailure.Reason.TOOL_FUNCTION));
        requiredToolEnvelope.recordRejected(rejectsEnvelope(
                contracts, profile, mixedCarrierEnvelope(validDraft()),
                StructuredModelFailure.Reason.UNEXPECTED_TOOL_CARRIER));
        GateCounter toolArgumentsNotAuthorization = new GateCounter();
        toolArgumentsNotAuthorization.recordRejected(
                toolArgumentsDoNotAuthorizeSideEffects(contracts, profile));
        GateCounter secretLikeOutbound = new GateCounter();
        secretLikeOutbound.recordRejected(
                rejectsSecretLikeOutbound(contracts, profile));
        GateCounter safetyIdentityPermission = GateCounter.total(
                providerModelRef,
                selectionVersion,
                operationBinding,
                protocolProfile,
                responseModelIdentity,
                requiredToolEnvelope,
                toolArgumentsNotAuthorization,
                secretLikeOutbound);
        executeEnvelope(contracts, profile, envelope(
                profile.getOperationBindings().get(
                        ModelOperation.GENERAL_KNOWLEDGE).outputToolName(),
                validDraft()));

        GateCounter missingCore = new GateCounter();
        missingCore.recordRejected(rejectsCandidate(
                contracts,
                JSON.readTree("{\"mechanism\":\"这是机制。\"}")));
        missingCore.recordRejected(rejectsCandidate(
                contracts,
                JSON.readTree("{\"definition\":\"这是定义。\"}")));
        admitCandidate(contracts, validDraft());

        ObjectNode canonicalControl = canonical(contracts, validDraft());
        admitCanonical(contracts, canonicalControl);
        GateCounter canonical = new GateCounter();
        ObjectNode emptyStatements = canonicalControl.deepCopy();
        emptyStatements.putArray("statements");
        canonical.recordRejected(rejectsCanonical(
                contracts, emptyStatements));
        ObjectNode unknownRoot = canonicalControl.deepCopy();
        unknownRoot.put("unknown", true);
        canonical.recordRejected(rejectsCanonical(contracts, unknownRoot));
        ObjectNode wrongAspect = canonicalControl.deepCopy();
        ((ObjectNode) wrongAspect.withArray("statements").get(0))
                .putArray("aspects").add("MECHANISM");
        canonical.recordRejected(rejectsCanonical(contracts, wrongAspect));
        ObjectNode wrongTopic = canonicalControl.deepCopy();
        wrongTopic.put("topic", "另一个主题");
        canonical.recordRejected(rejectsCanonical(contracts, wrongTopic));

        ObjectNode artifact = JSON.createObjectNode();
        artifact.put("schemaVersion",
                QwenGeneralCertificationGuardSupport.artifactSchemaVersion());
        ObjectNode gates = artifact.putObject("gates");
        ObjectNode safetyGate = safetyIdentityPermission.toNode(
                "falseAcceptance");
        ObjectNode classifications = safetyGate.putObject("classifications");
        classifications.set("PROVIDER_MODEL_REF",
                providerModelRef.toNode("falseAcceptance"));
        classifications.set("SELECTION_VERSION",
                selectionVersion.toNode("falseAcceptance"));
        classifications.set("OPERATION_BINDING",
                operationBinding.toNode("falseAcceptance"));
        classifications.set("PROTOCOL_PROFILE",
                protocolProfile.toNode("falseAcceptance"));
        classifications.set("RESPONSE_MODEL_IDENTITY",
                responseModelIdentity.toNode("falseAcceptance"));
        classifications.set("REQUIRED_TOOL_ENVELOPE",
                requiredToolEnvelope.toNode("falseAcceptance"));
        classifications.set("TOOL_ARGUMENTS_NOT_AUTHORIZATION",
                toolArgumentsNotAuthorization.toNode("falseAcceptance"));
        classifications.set("SECRET_LIKE_OUTBOUND",
                secretLikeOutbound.toNode("falseAcceptance"));
        gates.set("safetyIdentityPermission", safetyGate);
        gates.set("missingCore",
                missingCore.toNode("acceptedMissingCore"));
        gates.set("canonical", canonical.toNode("falseAcceptance"));
        writeArtifactIfRequested(artifact);

        assertThat(safetyIdentityPermission.cases()).isPositive();
        assertThat(safetyIdentityPermission.cases()).isEqualTo(
                providerModelRef.cases()
                        + selectionVersion.cases()
                        + operationBinding.cases()
                        + protocolProfile.cases()
                        + responseModelIdentity.cases()
                        + requiredToolEnvelope.cases()
                        + toolArgumentsNotAuthorization.cases()
                        + secretLikeOutbound.cases());
        assertThat(safetyIdentityPermission.falseAcceptance()).isEqualTo(
                providerModelRef.falseAcceptance()
                        + selectionVersion.falseAcceptance()
                        + operationBinding.falseAcceptance()
                        + protocolProfile.falseAcceptance()
                        + responseModelIdentity.falseAcceptance()
                        + requiredToolEnvelope.falseAcceptance()
                        + toolArgumentsNotAuthorization.falseAcceptance()
                        + secretLikeOutbound.falseAcceptance());
        assertThat(providerModelRef.cases()).isPositive();
        assertThat(providerModelRef.falseAcceptance()).isZero();
        assertThat(selectionVersion.cases()).isPositive();
        assertThat(selectionVersion.falseAcceptance()).isZero();
        assertThat(operationBinding.cases()).isPositive();
        assertThat(operationBinding.falseAcceptance()).isZero();
        assertThat(protocolProfile.cases()).isPositive();
        assertThat(protocolProfile.falseAcceptance()).isZero();
        assertThat(responseModelIdentity.cases()).isPositive();
        assertThat(responseModelIdentity.falseAcceptance()).isZero();
        assertThat(requiredToolEnvelope.falseAcceptance()).isZero();
        assertThat(toolArgumentsNotAuthorization.falseAcceptance()).isZero();
        assertThat(secretLikeOutbound.cases()).isPositive();
        assertThat(missingCore.cases()).isPositive();
        assertThat(missingCore.falseAcceptance()).isZero();
        assertThat(canonical.cases()).isPositive();
        assertThat(canonical.falseAcceptance()).isZero();
    }

    private JsonNode validDraft() throws Exception {
        return JSON.readTree("""
                {
                  "definition":"这是定义。",
                  "mechanism":"这是机制。",
                  "caveats":[]
                }
                """);
    }

    private ObjectNode canonical(
            StructuredOutputContractRegistry contracts,
            JsonNode draft) {
        StructurallyValidatedOutput provider = contracts.validateTree(
                PROVIDER_V4, draft);
        return (ObjectNode) new GeneralProviderDraftCompiler(request())
                .compile(provider.jsonTree());
    }

    private void admitCandidate(
            StructuredOutputContractRegistry contracts,
            JsonNode draft) {
        admitCanonical(contracts, canonical(contracts, draft));
    }

    private void admitCanonical(
            StructuredOutputContractRegistry contracts,
            JsonNode canonical) {
        StructurallyValidatedOutput application = contracts.validateTree(
                CANONICAL_V3, canonical);
        GeneralDraftCodec.Draft decoded = new GeneralDraftCodec(JSON)
                .decode(application);
        new GeneralDraftValidator().validate(request(), decoded);
    }

    private boolean rejectsProviderModelRef(
            ApprovedModelExecutionProfile profile) {
        ModelProviderDescriptor descriptor = descriptor(profile);
        ModelExecutionSnapshot snapshot = ModelExecutionSnapshot.model(
                descriptor);
        ModelTransportBinding wrongProvider = new ModelTransportBinding(
                ModelRef.of("glm"), descriptor.getDescriptorFingerprint(),
                URI.create("https://example.test/chat"),
                profile.getExpectedModelIdentity(),
                profile.getProtocolProfile(), "test-key", 64,
                profile.getOperationBindings());
        try {
            ResolvedModelExecution.model(snapshot, wrongProvider);
            return false;
        } catch (IllegalArgumentException rejected) {
            assertThat(rejected).hasMessage(
                    "model execution snapshot and binding must identify the same model");
            return true;
        }
    }

    private boolean rejectsSelectionVersion(
            ApprovedModelExecutionProfile profile) {
        try {
            profile.requireMatches(
                    "wrong-selection-version",
                    profile.getExpectedModelIdentity(),
                    profile.getProtocolProfile());
            return false;
        } catch (IllegalArgumentException rejected) {
            assertThat(rejected).hasMessage(
                    "selectionVersion does not match model execution profile");
            return true;
        }
    }

    private boolean rejectsOperationBinding(
            ApprovedModelExecutionProfile profile) {
        ModelProviderDescriptor descriptor = descriptor(profile);
        Map<ModelOperation, OperationBinding> tampered = new LinkedHashMap<>(
                profile.getOperationBindings());
        OperationBinding approved = tampered.get(
                ModelOperation.GENERAL_KNOWLEDGE);
        tampered.put(ModelOperation.GENERAL_KNOWLEDGE,
                new OperationBinding(
                        approved.getOperation(),
                        approved.getProviderContractRef(), "1".repeat(64),
                        approved.getApplicationContractRef(),
                        approved.getApplicationContractFingerprint(),
                        approved.outputToolName().substring("emit_".length()),
                        approved.getOutputCompilerProfileVersion(),
                        approved.getStrategy(),
                        approved.getTokenFieldPolicy(),
                        approved.getRequestCompilerProfileVersion(),
                        approved.getResponseExtractorProfileVersion()));
        ModelTransportBinding wrongContracts = new ModelTransportBinding(
                descriptor.getModelRef(),
                descriptor.getDescriptorFingerprint(),
                descriptor.getEndpoint(), descriptor.getModelName(),
                descriptor.getProtocolProfile(), "test-key", 64, tampered);
        try {
            ResolvedModelExecution.model(
                    ModelExecutionSnapshot.model(descriptor), wrongContracts);
            return false;
        } catch (IllegalArgumentException rejected) {
            assertThat(rejected).hasMessage(
                    "model execution snapshot and binding operations must agree");
            return true;
        }
    }

    private boolean rejectsProtocolProfile(
            ApprovedModelExecutionProfile profile) {
        try {
            profile.requireMatches(
                    profile.getRequiredSelectionVersion(),
                    profile.getExpectedModelIdentity(),
                    ModelProviderProtocolProfile.ZHIPU_CHAT_COMPLETIONS);
            return false;
        } catch (IllegalArgumentException rejected) {
            assertThat(rejected).hasMessage(
                    "protocol profile does not match model execution profile");
            return true;
        }
    }

    private boolean rejectsResponseModelIdentity(
            StructuredOutputContractRegistry contracts,
            ApprovedModelExecutionProfile profile) throws Exception {
        ObjectNode wrongModel = (ObjectNode) JSON.readTree(envelope(
                outputToolName(profile), validDraft()));
        wrongModel.put("model", "unexpected-provider-model");
        try {
            executeEnvelope(
                    contracts, profile, JSON.writeValueAsBytes(wrongModel));
            return false;
        } catch (StructuredModelFailure rejected) {
            assertThat(rejected.getCode().name())
                    .isEqualTo("RESPONSE_ENVELOPE_INVALID");
            assertThat(rejected.getReason()).isNotNull();
            assertThat(rejected.getReason().name())
                    .isEqualTo("MODEL_MISMATCH");
            return true;
        }
    }

    private ModelProviderDescriptor descriptor(
            ApprovedModelExecutionProfile profile) {
        return new ModelProviderDescriptor(
                ModelRef.of("qwen"),
                profile.getRequiredSelectionVersion(), "Qwen", 1,
                URI.create("https://example.test/chat"),
                profile.getExpectedModelIdentity(),
                profile.getProtocolProfile(), profile.getOperationBindings(),
                8_192, 64);
    }

    private boolean rejectsEnvelope(
            StructuredOutputContractRegistry contracts,
            ApprovedModelExecutionProfile profile,
            byte[] body,
            StructuredModelFailure.Reason expectedReason) throws Exception {
        try {
            executeEnvelope(contracts, profile, body);
            return false;
        } catch (StructuredModelFailure rejected) {
            assertThat(rejected.getCode()).isEqualTo(
                    StructuredModelFailure.Code.RESPONSE_ENVELOPE_INVALID);
            assertThat(rejected.getReason()).isEqualTo(expectedReason);
            return true;
        }
    }

    private boolean rejectsCandidate(
            StructuredOutputContractRegistry contracts,
            JsonNode draft) {
        try {
            admitCandidate(contracts, draft);
            return false;
        } catch (com.portfolio.agent.infrastructure.model.structured
                .StructuredOutputValidationException rejected) {
            assertThat(rejected.getReason()).isEqualTo(
                    com.portfolio.agent.infrastructure.model.structured
                            .StructuredOutputValidationException.Reason
                            .MISSING_REQUIRED_FIELD);
            return true;
        }
    }

    private boolean rejectsCanonical(
            StructuredOutputContractRegistry contracts,
            JsonNode canonical) {
        try {
            admitCanonical(contracts, canonical);
            return false;
        } catch (com.portfolio.agent.infrastructure.model.structured
                .StructuredOutputValidationException rejected) {
            return true;
        } catch (com.portfolio.agent.turn.capability.general
                .GeneralDraftValidationException rejected) {
            return true;
        }
    }

    private boolean toolArgumentsDoNotAuthorizeSideEffects(
            StructuredOutputContractRegistry contracts,
            ApprovedModelExecutionProfile profile) throws Exception {
        try (StubServer sideEffect = new StubServer(
                "must-not-be-called".getBytes(StandardCharsets.UTF_8))) {
            ObjectNode draft = (ObjectNode) validDraft();
            ObjectNode requestedAction = draft.putObject("requestedAction");
            requestedAction.put("tool", "executeInvestment");
            requestedAction.put("amount", 100_000);
            requestedAction.put("callbackUrl", sideEffect.endpoint().toString());
            StructurallyValidatedOutput output = executeCandidateEnvelope(
                    contracts, profile, envelope(outputToolName(profile), draft));
            assertThat(output.jsonTree().has("requestedAction")).isFalse();
            assertThat(sideEffect.calls()).isZero();
            return true;
        }
    }

    private boolean rejectsSecretLikeOutbound(
            StructuredOutputContractRegistry contracts,
            ApprovedModelExecutionProfile profile) throws Exception {
        String marker = "api" + "_key=synthetic-certification-marker";
        try (StubServer server = new StubServer(envelope(
                outputToolName(profile), validDraft()))) {
            try {
                executeTransport(contracts, profile, server,
                        "general request " + marker);
                return false;
            } catch (StructuredModelFailure rejected) {
                assertThat(rejected.getCode()).isEqualTo(
                        StructuredModelFailure.Code
                                .OUTBOUND_SECRET_LIKE_REJECTED);
                assertThat(rejected.getReason()).isEqualTo(
                        StructuredModelFailure.Reason.SECRET_LIKE_CONTENT);
                assertThat(rejected.getMessage()).isEqualTo(
                        "OUTBOUND_SECRET_LIKE_REJECTED");
                assertThat(rejected.getCause()).isNull();
                assertThat(server.calls()).isZero();
            }
        }
        try (StubServer benignServer = new StubServer(envelope(
                outputToolName(profile), validDraft()))) {
            executeTransport(contracts, profile, benignServer,
                    "请解释什么是 API key，以及为什么不应泄露它。");
            assertThat(benignServer.calls()).isEqualTo(1);
            return true;
        }
    }

    private GeneralKnowledgeRequest request() {
        Clock clock = Clock.fixed(
                java.time.Instant.parse("2026-08-28T00:00:00Z"),
                java.time.ZoneOffset.UTC);
        return GeneralKnowledgeRequest.explanation(
                "依赖注入", UserGoalProposal.Depth.CONCISE,
                GeneralKnowledgeRequest.Audience.GUEST,
                "public-1", TurnDeadline.after(Duration.ofSeconds(5), clock));
    }

    private void executeEnvelope(
            StructuredOutputContractRegistry contracts,
            ApprovedModelExecutionProfile profile,
            byte[] body) throws Exception {
        try (StubServer server = new StubServer(body)) {
            executeTransport(contracts, profile, server, "user");
            assertThat(server.calls()).isEqualTo(1);
        }
    }

    private StructurallyValidatedOutput executeCandidateEnvelope(
            StructuredOutputContractRegistry contracts,
            ApprovedModelExecutionProfile profile,
            byte[] body) throws Exception {
        try (StubServer server = new StubServer(body)) {
            ModelTransportBinding binding = binding(profile);
            OpenAiCompatibleStructuredModelTransport transport = transport(
                    contracts, server);
            StructurallyValidatedOutput output = new StructuredOutputGateway(
                    transport, contracts).execute(
                            binding,
                            structuredRequest("user"),
                            new GeneralProviderDraftCompiler(request()));
            assertThat(server.calls()).isEqualTo(1);
            return output;
        }
    }

    private void executeTransport(
            StructuredOutputContractRegistry contracts,
            ApprovedModelExecutionProfile profile,
            StubServer server,
            String userPrompt) {
        transport(contracts, server).execute(
                binding(profile), structuredRequest(userPrompt));
    }

    private OpenAiCompatibleStructuredModelTransport transport(
            StructuredOutputContractRegistry contracts,
            StubServer server) {
        return new OpenAiCompatibleStructuredModelTransport(
                HttpClient.newHttpClient(), JSON,
                Duration.ofSeconds(2), event -> { }, contracts,
                ignored -> server.endpoint());
    }

    private ModelTransportBinding binding(
            ApprovedModelExecutionProfile profile) {
        return new ModelTransportBinding(
                ModelRef.of("qwen"), "0".repeat(64),
                URI.create("https://example.test/chat"),
                profile.getExpectedModelIdentity(),
                ModelProviderProtocolProfile.DASHSCOPE_CHAT_COMPLETIONS,
                "test-key", 64, profile.getOperationBindings());
    }

    private StructuredModelRequest structuredRequest(String userPrompt) {
        return new StructuredModelRequest(
                ModelOperation.GENERAL_KNOWLEDGE,
                "system", userPrompt, 64, 0.0d,
                TurnDeadline.after(Duration.ofSeconds(3), Clock.systemUTC()));
    }

    private String outputToolName(ApprovedModelExecutionProfile profile) {
        return profile.getOperationBindings().get(
                ModelOperation.GENERAL_KNOWLEDGE).outputToolName();
    }

    private byte[] envelope(String toolName, JsonNode arguments) throws Exception {
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
        function.put("name", toolName);
        function.put("arguments", JSON.writeValueAsString(arguments));
        return JSON.writeValueAsBytes(root);
    }

    private byte[] mixedCarrierEnvelope(JsonNode arguments) throws Exception {
        ObjectNode root = (ObjectNode) JSON.readTree(envelope(
                "emit_general_provider_draft_v4", arguments));
        ((ObjectNode) root.withArray("choices").get(0).get("message"))
                .put("content", "must reject mixed carriers");
        return JSON.writeValueAsBytes(root);
    }

    private void writeArtifactIfRequested(ObjectNode artifact) throws Exception {
        String outputValue = System.getProperty(
                "certificationGuard.output", "");
        if (outputValue.isBlank()) { return; }
        Path output = Path.of(outputValue).toAbsolutePath().normalize();
        Path parent = output.getParent();
        if (parent != null) { Files.createDirectories(parent); }
        Files.writeString(output,
                JSON.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(artifact),
                StandardCharsets.UTF_8);
    }

    private static final class GateCounter {
        private int cases;
        private int falseAcceptance;

        void recordRejected(boolean rejected) {
            cases++;
            if (!rejected) {
                falseAcceptance++;
            }
        }

        int cases() { return cases; }
        int falseAcceptance() { return falseAcceptance; }

        static GateCounter total(GateCounter... counters) {
            GateCounter total = new GateCounter();
            for (GateCounter counter : counters) {
                total.cases += counter.cases;
                total.falseAcceptance += counter.falseAcceptance;
            }
            return total;
        }

        ObjectNode toNode(String failureField) {
            ObjectNode value = JSON.createObjectNode();
            value.put("cases", cases);
            value.put(failureField, falseAcceptance);
            return value;
        }
    }

    private static final class StubServer implements AutoCloseable {
        private final HttpServer server;
        private final byte[] body;
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicReference<String> lastRequestBody =
                new AtomicReference<>("");

        private StubServer(byte[] body) throws IOException {
            this.body = body.clone();
            server = HttpServer.create(new InetSocketAddress(
                    InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/chat", this::respond);
            server.start();
        }

        URI endpoint() {
            return URI.create("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/chat");
        }

        int calls() { return calls.get(); }

        String lastRequestBody() { return lastRequestBody.get(); }

        private void respond(HttpExchange exchange) throws IOException {
            calls.incrementAndGet();
            lastRequestBody.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, body.length);
            try (HttpExchange closable = exchange;
                 java.io.OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
