package com.portfolio.agent.infrastructure.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticLevel;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderProtocolProfile;
import com.portfolio.agent.infrastructure.model.provider.ModelRef;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.structured.StructuredModelTestFixtures;
import com.portfolio.agent.infrastructure.model.structured.TokenFieldPolicy;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputStrategy;
import com.portfolio.agent.turn.execution.TurnDeadline;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class OpenAiCompatibleStructuredModelTransportProtocolTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void zhipuUsesOnlyItsClosedThinkingShapeAndFixedAuthorizationHeader() throws Exception {
        try (StubServer server = StubServer.responding(200, successBody())) {
            List<DiagnosticEvent> events = new ArrayList<>();
            transport(server.endpoint(), events::add).execute(
                    binding("glm", "glm-4.7-flash",
                            ModelProviderProtocolProfile.ZHIPU_CHAT_COMPLETIONS),
                    request());

            JsonNode payload = MAPPER.readTree(server.requestBody.get());
            assertCommonPayload(payload, "glm-4.7-flash");
            assertThat(payload.path("thinking").path("type").textValue())
                    .isEqualTo("disabled");
            assertThat(payload.has("enable_thinking")).isFalse();
            assertThat(server.authorization.get()).isEqualTo("Bearer test-key");
            assertThat(server.contentType.get()).isEqualTo("application/json");
            assertThat(events).singleElement().satisfies(event -> {
                assertThat(event.getName()).isEqualTo("provider.call.completed");
                assertThat(event.getLevel()).isEqualTo(DiagnosticLevel.INFO);
                assertThat(event.getFields())
                        .containsEntry("attempt.index", 1)
                        .containsEntry("attempt.count", 1)
                        .containsEntry("duplicate.billing.risk", false)
                        .containsEntry("usage.present", false)
                        .doesNotContainKeys(
                                "usage.input_tokens.bucket",
                                "usage.output_tokens.bucket",
                                "usage.total_tokens.bucket");
            });
        }
    }

    @Test
    void attemptAwareDiagnosticsBucketProviderUsageWithoutLoggingIdentityOrCost()
            throws Exception {
        byte[] response = successBodyWithUsage(0, 256, 65_000);
        UUID privateAttemptId = UUID.fromString(
                "123e4567-e89b-12d3-a456-426614174000");
        try (StubServer server = StubServer.responding(200, response)) {
            List<DiagnosticEvent> events = new ArrayList<>();

            transport(server.endpoint(), events::add).execute(
                    binding("qwen", "qwen3.7-flash",
                            ModelProviderProtocolProfile
                                    .DASHSCOPE_CHAT_COMPLETIONS),
                    request(),
                    new ProviderAttemptContext(privateAttemptId, 2, 2, true));

            assertThat(events).singleElement().satisfies(event -> {
                assertThat(event.getFields())
                        .containsEntry("attempt.index", 2)
                        .containsEntry("attempt.count", 2)
                        .containsEntry("duplicate.billing.risk", true)
                        .containsEntry("usage.present", true)
                        .containsEntry("usage.input_tokens.bucket", "ZERO")
                        .containsEntry(
                                "usage.output_tokens.bucket",
                                "FROM_256_TO_1023")
                        .containsEntry(
                                "usage.total_tokens.bucket", "GTE_4096");
                assertThat(event.toString())
                        .doesNotContain(privateAttemptId.toString(), "cost");
            });
        }
    }

    @Test
    void malformedOrPartialUsageIsExplicitlyUnavailableWithoutRejectingContent()
            throws Exception {
        List<String> usageBodies = List.of(
                "{\"prompt_tokens\":-1,\"completion_tokens\":2,\"total_tokens\":1}",
                "{\"prompt_tokens\":1.5,\"completion_tokens\":2,\"total_tokens\":3}",
                "{\"prompt_tokens\":1,\"completion_tokens\":2}",
                "\"private-usage-sentinel\"");

        for (String usageBody : usageBodies) {
            byte[] response = successBodyWithUsage(usageBody);
            try (StubServer server = StubServer.responding(200, response)) {
                List<DiagnosticEvent> events = new ArrayList<>();

                StructuredModelResponse result = transport(
                        server.endpoint(), events::add).execute(
                        binding("qwen", "qwen3.7-flash",
                                ModelProviderProtocolProfile
                                        .DASHSCOPE_CHAT_COMPLETIONS),
                        request());

                assertThat(result).isNotNull();
                assertThat(events).singleElement().satisfies(event -> {
                    assertThat(event.getFields())
                            .containsEntry("usage.present", false)
                            .doesNotContainKeys(
                                    "usage.input_tokens.bucket",
                                    "usage.output_tokens.bucket",
                                    "usage.total_tokens.bucket");
                    assertThat(event.toString()).doesNotContain(
                            "private-usage-sentinel");
                });
            }
        }
    }

    @Test
    void dashscopeUsesOnlyItsClosedThinkingShapeAndCapsOutputTokens() throws Exception {
        try (StubServer server = StubServer.responding(200, successBody())) {
            transport(server.endpoint(), event -> { }).execute(
                    binding("qwen", "qwen3.7-flash",
                            ModelProviderProtocolProfile.DASHSCOPE_CHAT_COMPLETIONS),
                    request());

            JsonNode payload = MAPPER.readTree(server.requestBody.get());
            assertCommonPayload(payload, "qwen3.7-flash");
            assertThat(payload.path("enable_thinking").booleanValue()).isFalse();
            assertThat(payload.has("thinking")).isFalse();
            assertThat(payload.path("max_tokens").intValue()).isEqualTo(32);
        }
    }

    @Test
    void diagnosticsFailureNeverChangesAValidProviderResult() throws Exception {
        try (StubServer server = StubServer.responding(200, successBody())) {
            StructuredModelResponse response = transport(
                    server.endpoint(), event -> {
                        throw new IllegalStateException("diagnostic-sentinel");
                    }).execute(binding("glm", "glm-4.7-flash",
                            ModelProviderProtocolProfile.ZHIPU_CHAT_COMPLETIONS),
                            request());

            assertThat(response).isNotNull();
            assertThat(server.requestCount).hasValue(1);
        }
    }

    @Test
    void requiredToolStrategyForcesOneSyntheticToolAndExtractsOnlyItsArguments()
            throws Exception {
        try (StubServer server = StubServer.responding(200, toolSuccessBody())) {
            StructuredModelResponse response = transport(
                    server.endpoint(), event -> { }).execute(
                    binding("glm", "glm-4.7-flash",
                            ModelProviderProtocolProfile.ZHIPU_CHAT_COMPLETIONS,
                            StructuredModelTestFixtures.toolBindings()),
                    request());

            JsonNode payload = MAPPER.readTree(server.requestBody.get());
            assertThat(payload.has("response_format")).isFalse();
            assertThat(payload.path("tools")).hasSize(1);
            assertThat(payload.path("tools").get(0).path("function")
                    .path("name").textValue()).isEqualTo("emit_general_draft");
            assertThat(payload.path("tools").get(0).path("function")
                    .path("parameters"))
                    .isEqualTo(StructuredModelTestFixtures.contracts().resolve(
                            new com.portfolio.agent.infrastructure.model.structured
                                    .StructuredContractRef(
                                    ModelOperation.GENERAL_KNOWLEDGE,
                                    "general.draft.v2")).canonicalSchema());
            assertThat(payload.path("tool_choice").path("function")
                    .path("name").textValue()).isEqualTo("emit_general_draft");
            assertThat(payload.path("parallel_tool_calls").booleanValue()).isFalse();
            assertThat(response).isNotNull();
        }
    }

    @Test
    void requiredToolStrategyAcceptsBlankNonCarrierContent() throws Exception {
        byte[] body = new String(toolSuccessBody(), StandardCharsets.UTF_8)
                .replace("\"message\":{",
                        "\"message\":{\"content\":\"\",")
                .getBytes(StandardCharsets.UTF_8);
        try (StubServer server = StubServer.responding(200, body)) {
            StructuredModelResponse response = transport(
                    server.endpoint(), event -> { }).execute(
                    binding("glm", "glm-4.7-flash",
                            ModelProviderProtocolProfile.ZHIPU_CHAT_COMPLETIONS,
                            StructuredModelTestFixtures.toolBindings()),
                    request());

            assertThat(response).isNotNull();
            assertThat(server.requestCount).hasValue(1);
        }
    }

    @Test
    void tokenFieldPolicyCanOmitMaxTokensForNativeStructuredOutput()
            throws Exception {
        try (StubServer server = StubServer.responding(200, successBody())) {
            transport(server.endpoint(), event -> { }).execute(
                    binding("qwen", "qwen3.7-flash",
                            ModelProviderProtocolProfile.DASHSCOPE_CHAT_COMPLETIONS,
                            StructuredModelTestFixtures.bindings(
                                    StructuredOutputStrategy.NATIVE_JSON_SCHEMA,
                                    TokenFieldPolicy.OMIT)),
                    request());

            assertThat(MAPPER.readTree(server.requestBody.get()).has("max_tokens"))
                    .isFalse();
        }
    }

    @Test
    void mixedOrUnexpectedResponseCarriersAreRejectedWithoutRetry() throws Exception {
        assertResponseFailure(
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":"
                        + "{\"content\":\"{}\",\"tool_calls\":[]}}]}",
                StructuredModelFailure.Code.RESPONSE_ENVELOPE_INVALID, "ENVELOPE");
    }

    @Test
    void oneStatelessTransportExecutesEachExplicitBindingWithoutLeakingPriorSelection()
            throws Exception {
        try (StubServer server = StubServer.responding(200, successBody())) {
            OpenAiCompatibleStructuredModelTransport transport =
                    transport(server.endpoint(), event -> { });

            transport.execute(binding("glm", "glm-4.7-flash",
                    ModelProviderProtocolProfile.ZHIPU_CHAT_COMPLETIONS), request());
            String glmPayload = server.requestBody.get();
            transport.execute(binding("qwen", "qwen3.7-flash",
                    ModelProviderProtocolProfile.DASHSCOPE_CHAT_COMPLETIONS), request());
            String qwenPayload = server.requestBody.get();

            assertThat(MAPPER.readTree(glmPayload).path("model").textValue())
                    .isEqualTo("glm-4.7-flash");
            assertThat(MAPPER.readTree(qwenPayload).path("model").textValue())
                    .isEqualTo("qwen3.7-flash");
            assertThat(MAPPER.readTree(glmPayload).has("enable_thinking")).isFalse();
            assertThat(MAPPER.readTree(qwenPayload).has("thinking")).isFalse();
            assertThat(server.requestCount).hasValue(2);
        }
    }

    @Test
    void httpFailuresHaveStableCategoriesWithoutRetainingProviderBodyOrRetrying() throws Exception {
        assertHttpFailure(401, StructuredModelFailure.Code.AUTHENTICATION_REJECTED);
        assertHttpFailure(403, StructuredModelFailure.Code.AUTHENTICATION_REJECTED);
        assertHttpFailure(402, StructuredModelFailure.Code.BILLING_REJECTED);
        assertHttpFailure(429, StructuredModelFailure.Code.RATE_LIMITED);
        assertHttpFailure(500, StructuredModelFailure.Code.PROVIDER_UNAVAILABLE);
        assertHttpFailure(502, StructuredModelFailure.Code.PROVIDER_UNAVAILABLE);
        assertHttpFailure(503, StructuredModelFailure.Code.PROVIDER_UNAVAILABLE);
        assertHttpFailure(504, StructuredModelFailure.Code.PROVIDER_UNAVAILABLE);
        assertHttpFailure(400, StructuredModelFailure.Code.PROVIDER_REJECTED);
    }

    @Test
    void rateLimitRetryAfterPreservesMissingValidAndInvalidClosedStates()
            throws Exception {
        assertRateLimitRetryAfter(
                null, null,
                StructuredModelFailure.RetryAfterDisposition.MISSING);
        assertRateLimitRetryAfter(
                "1", 1,
                StructuredModelFailure.RetryAfterDisposition.VALID);
        assertRateLimitRetryAfter(
                "17", 17,
                StructuredModelFailure.RetryAfterDisposition.VALID);
        assertRateLimitRetryAfter(
                "999", 300,
                StructuredModelFailure.RetryAfterDisposition.VALID);
        assertRateLimitRetryAfter(
                "0", 0,
                StructuredModelFailure.RetryAfterDisposition.VALID);
        assertRateLimitRetryAfter(
                "+0", null,
                StructuredModelFailure.RetryAfterDisposition.INVALID);
        assertRateLimitRetryAfter(
                "+1", null,
                StructuredModelFailure.RetryAfterDisposition.INVALID);
        assertRateLimitRetryAfter(
                "-1", null,
                StructuredModelFailure.RetryAfterDisposition.INVALID);
        assertRateLimitRetryAfter(
                "1 0", null,
                StructuredModelFailure.RetryAfterDisposition.INVALID);
        assertRateLimitRetryAfter(
                "9".repeat(1_000), 300,
                StructuredModelFailure.RetryAfterDisposition.VALID);
        assertRateLimitRetryAfter(
                "not-a-number", null,
                StructuredModelFailure.RetryAfterDisposition.INVALID);
        assertRateLimitRetryAfter(
                "Wed, 21 Oct 2015 07:28:00 GMT", null,
                StructuredModelFailure.RetryAfterDisposition.INVALID);
    }

    @Test
    void providerFailureNeverExecutesTheOtherConfiguredBinding() throws Exception {
        assertNoFallback(
                binding("glm", "glm-4.7-flash",
                        ModelProviderProtocolProfile.ZHIPU_CHAT_COMPLETIONS),
                binding("qwen", "qwen3.7-flash",
                        ModelProviderProtocolProfile.DASHSCOPE_CHAT_COMPLETIONS));
        assertNoFallback(
                binding("qwen", "qwen3.7-flash",
                        ModelProviderProtocolProfile.DASHSCOPE_CHAT_COMPLETIONS),
                binding("glm", "glm-4.7-flash",
                        ModelProviderProtocolProfile.ZHIPU_CHAT_COMPLETIONS));
    }

    @Test
    void jsonAndEnvelopeFailuresAreClosedAndNeverRetried() throws Exception {
        assertResponseFailure("", StructuredModelFailure.Code.RESPONSE_JSON_INVALID, "JSON");
        assertResponseFailure("not-json", StructuredModelFailure.Code.RESPONSE_JSON_INVALID, "JSON");
        assertResponseFailure(
                "{\"choices\":[],\"choices\":[]}",
                StructuredModelFailure.Code.RESPONSE_JSON_INVALID, "JSON");
        assertResponseFailure(
                "{\"choices\":[]} {}",
                StructuredModelFailure.Code.RESPONSE_JSON_INVALID, "JSON");
        assertResponseFailure("{}", StructuredModelFailure.Code.RESPONSE_ENVELOPE_INVALID, "ENVELOPE");
        assertResponseFailure(
                "{\"choices\":[{\"message\":{\"content\":\"\"}}]}",
                StructuredModelFailure.Code.RESPONSE_ENVELOPE_INVALID, "ENVELOPE");
        assertResponseFailure(
                "{\"choices\":[{\"message\":{\"content\":\"a\"}},"
                        + "{\"message\":{\"content\":\"b\"}}]}",
                StructuredModelFailure.Code.RESPONSE_ENVELOPE_INVALID, "ENVELOPE");
    }

    @Test
    void invalidJsonFailureRetainsNoParserOrRawResponseObject()
            throws Exception {
        String sentinel = "private-invalid-json-sentinel";
        try (StubServer server = StubServer.responding(
                200, ("{" + sentinel).getBytes(StandardCharsets.UTF_8))) {
            StructuredModelFailure failure = catchThrowableOfType(
                    () -> transport(server.endpoint(), event -> { }).execute(
                            binding("glm", "glm-4.7-flash",
                                    ModelProviderProtocolProfile
                                            .ZHIPU_CHAT_COMPLETIONS),
                            request()),
                    StructuredModelFailure.class);

            assertThat(failure.getCode()).isEqualTo(
                    StructuredModelFailure.Code.RESPONSE_JSON_INVALID);
            assertThat(failure.getReason()).isEqualTo(
                    StructuredModelFailure.Reason.MALFORMED_JSON);
            assertThat(failure.getCause()).isNull();
            assertThat(failure.getSuppressed()).isEmpty();
            assertThat(failure.toString()).doesNotContain(sentinel);
            for (Field field : StructuredModelFailure.class
                    .getDeclaredFields()) {
                assertThat(field.getType()).isNotEqualTo(byte[].class);
            }
        }
    }

    @Test
    void responseBodyIsRejectedAtTheHardByteLimit() throws Exception {
        byte[] oversized = new byte[OpenAiCompatibleStructuredModelTransport.MAX_RESPONSE_BYTES + 1];
        try (StubServer server = StubServer.responding(200, oversized)) {
            List<DiagnosticEvent> events = new ArrayList<>();
            StructuredModelFailure failure = catchThrowableOfType(
                    () -> transport(server.endpoint(), events::add).execute(
                            binding("glm", "glm-4.7-flash",
                                    ModelProviderProtocolProfile.ZHIPU_CHAT_COMPLETIONS),
                            request()),
                    StructuredModelFailure.class);

            assertThat(failure.getCode()).isEqualTo(StructuredModelFailure.Code.RESPONSE_TOO_LARGE);
            assertThat(events).anySatisfy(event -> {
                assertThat(event.getFields().get("failure.code")).isEqualTo("RESPONSE_TOO_LARGE");
                assertThat(event.getFields().get("failure.layer")).isEqualTo("TRANSPORT");
            });
        }
    }

    private void assertCommonPayload(JsonNode payload, String expectedModel) {
        assertThat(payload.path("model").textValue()).isEqualTo(expectedModel);
        assertThat(payload.path("response_format").path("type").textValue())
                .isEqualTo("json_schema");
        assertThat(payload.path("response_format").path("json_schema")
                .path("strict").booleanValue()).isTrue();
        assertThat(payload.path("response_format").path("json_schema")
                .path("schema"))
                .isEqualTo(StructuredModelTestFixtures.contracts().resolve(
                        new com.portfolio.agent.infrastructure.model.structured
                                .StructuredContractRef(
                                ModelOperation.GENERAL_KNOWLEDGE,
                                "general.draft.v2")).canonicalSchema());
        assertThat(payload.path("stream").booleanValue()).isFalse();
        assertThat(payload.path("messages").isArray()).isTrue();
        assertThat(payload.path("messages")).hasSize(2);
        assertThat(payload.path("temperature").doubleValue()).isZero();
    }

    private void assertHttpFailure(int status, StructuredModelFailure.Code expected) throws Exception {
        String sentinel = "provider-secret-body-must-not-be-retained";
        try (StubServer server = StubServer.responding(status,
                sentinel.getBytes(StandardCharsets.UTF_8))) {
            List<DiagnosticEvent> events = new ArrayList<>();
            StructuredModelFailure failure = catchThrowableOfType(
                    () -> transport(server.endpoint(), events::add).execute(
                            binding("qwen", "qwen3.7-flash",
                                    ModelProviderProtocolProfile.DASHSCOPE_CHAT_COMPLETIONS),
                            request()),
                    StructuredModelFailure.class);

            assertThat(failure.getCode()).isEqualTo(expected);
            assertThat(failure.getHttpStatus()).isEqualTo(status);
            assertThat(failure).hasMessage(expected.name());
            assertThat(server.requestCount)
                    .as("HTTP rejection must not retry or switch provider").hasValue(1);
            assertThat(MAPPER.readTree(server.requestBody.get()).path("model").textValue())
                    .isEqualTo("qwen3.7-flash");
            assertThat(events.toString()).doesNotContain(sentinel);
            assertThat(events).singleElement().satisfies(event ->
                    assertThat(event.getFields())
                            .containsEntry("attempt.index", 1)
                            .containsEntry("attempt.count", 1)
                            .containsEntry("duplicate.billing.risk", false)
                            .containsEntry("usage.present", false));
        }
    }

    private void assertNoFallback(
            ModelTransportBinding selected,
            ModelTransportBinding other) throws Exception {
        try (StubServer selectedServer = StubServer.responding(500,
                "selected-failure".getBytes(StandardCharsets.UTF_8));
             StubServer otherServer = StubServer.responding(200, successBody())) {
            OpenAiCompatibleStructuredModelTransport transport =
                    new OpenAiCompatibleStructuredModelTransport(
                            HttpClient.newHttpClient(), MAPPER,
                            Duration.ofSeconds(2), event -> { },
                            StructuredModelTestFixtures.contracts(),
                            binding -> binding.getModelRef().equals(selected.getModelRef())
                                    ? selectedServer.endpoint() : otherServer.endpoint());

            StructuredModelFailure failure = catchThrowableOfType(
                    () -> transport.execute(selected, request()),
                    StructuredModelFailure.class);

            assertThat(failure.getCode())
                    .isEqualTo(StructuredModelFailure.Code.PROVIDER_UNAVAILABLE);
            assertThat(selectedServer.requestCount).hasValue(1);
            assertThat(otherServer.requestCount)
                    .as("the unselected binding must never be used as fallback")
                    .hasValue(0);
            assertThat(other.getModelRef()).isNotEqualTo(selected.getModelRef());
        }
    }

    private void assertRateLimitRetryAfter(
            String header, Integer expectedSeconds,
            StructuredModelFailure.RetryAfterDisposition expectedDisposition)
            throws Exception {
        try (StubServer server = StubServer.responding(
                429, "provider-body".getBytes(StandardCharsets.UTF_8), header)) {
            StructuredModelFailure failure = catchThrowableOfType(
                    () -> transport(server.endpoint(), event -> { }).execute(
                            binding("qwen", "qwen3.7-flash",
                                    ModelProviderProtocolProfile.DASHSCOPE_CHAT_COMPLETIONS),
                            request()),
                    StructuredModelFailure.class);

            assertThat(failure.getCode())
                    .isEqualTo(StructuredModelFailure.Code.RATE_LIMITED);
            assertThat(failure.getHttpStatus()).isEqualTo(429);
            assertThat(failure.getRetryAfterSeconds()).isEqualTo(expectedSeconds);
            assertThat(failure.getRetryAfterDisposition())
                    .isEqualTo(expectedDisposition);
        }
    }

    private void assertResponseFailure(
            String body, StructuredModelFailure.Code expected, String layer) throws Exception {
        try (StubServer server = StubServer.responding(200,
                body.getBytes(StandardCharsets.UTF_8))) {
            List<DiagnosticEvent> events = new ArrayList<>();
            StructuredModelFailure failure = catchThrowableOfType(
                    () -> transport(server.endpoint(), events::add).execute(
                            binding("glm", "glm-4.7-flash",
                                    ModelProviderProtocolProfile.ZHIPU_CHAT_COMPLETIONS),
                            request()),
                    StructuredModelFailure.class);

            assertThat(failure.getCode()).isEqualTo(expected);
            assertThat(server.requestCount)
                    .as("invalid response must not trigger repair or retry").hasValue(1);
            assertThat(events).anySatisfy(event -> {
                assertThat(event.getFields().get("failure.code")).isEqualTo(expected.name());
                assertThat(event.getFields().get("failure.layer")).isEqualTo(layer);
            });
        }
    }

    private ModelTransportBinding binding(
            String ref, String modelName, ModelProviderProtocolProfile profile) {
        return binding(ref, modelName, profile,
                StructuredModelTestFixtures.nativeBindings());
    }

    private ModelTransportBinding binding(
            String ref, String modelName, ModelProviderProtocolProfile profile,
            java.util.Map<com.portfolio.agent.infrastructure.model.policy.ModelOperation,
                    com.portfolio.agent.infrastructure.model.structured.OperationBinding>
                    operationBindings) {
        return new ModelTransportBinding(
                ModelRef.of(ref), "0".repeat(64),
                URI.create("https://example.test/chat"),
                modelName, profile, "test-key", 32,
                operationBindings);
    }

    private OpenAiCompatibleStructuredModelTransport transport(
            URI endpoint,
            com.portfolio.agent.common.observability.DiagnosticEventPublisher diagnostics) {
        return new OpenAiCompatibleStructuredModelTransport(
                HttpClient.newHttpClient(), MAPPER,
                Duration.ofSeconds(2), diagnostics,
                StructuredModelTestFixtures.contracts(), ignored -> endpoint);
    }

    private StructuredModelRequest request() {
        return new StructuredModelRequest(
                ModelOperation.GENERAL_KNOWLEDGE, "system JSON", "user", 64, 0.0d,
                TurnDeadline.after(Duration.ofSeconds(3), Clock.systemUTC()));
    }

    private static byte[] successBody() {
        return "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"answer\\\":\\\"ok\\\"}\"}}]}"
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] successBodyWithUsage(
            long promptTokens, long completionTokens, long totalTokens) {
        return successBodyWithUsage(
                "{\"prompt_tokens\":" + promptTokens
                        + ",\"completion_tokens\":" + completionTokens
                        + ",\"total_tokens\":" + totalTokens + "}");
    }

    private static byte[] successBodyWithUsage(String usage) {
        return ("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":"
                + "{\"content\":\"{\\\"answer\\\":\\\"ok\\\"}\"}}],"
                + "\"usage\":" + usage + "}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] toolSuccessBody() {
        return """
                {"choices":[{"finish_reason":"tool_calls","message":{
                  "tool_calls":[{"type":"function","function":{
                    "name":"emit_general_draft",
                    "arguments":"{\\"general\\":\\"ok\\"}"
                  }}]
                }}]}
                """.getBytes(StandardCharsets.UTF_8);
    }

    private static final class StubServer implements AutoCloseable {
        private final AtomicReference<String> requestBody = new AtomicReference<>();
        private final AtomicReference<String> authorization = new AtomicReference<>();
        private final AtomicReference<String> contentType = new AtomicReference<>();
        private final AtomicInteger requestCount = new AtomicInteger();
        private final HttpServer server;

        private StubServer(
                int status, byte[] responseBody,
                String retryAfter) throws IOException {
            server = HttpServer.create(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/chat", exchange ->
                    respond(exchange, status, responseBody, retryAfter));
            server.start();
        }

        static StubServer responding(int status, byte[] responseBody) throws IOException {
            return new StubServer(status, responseBody, null);
        }

        static StubServer responding(
                int status, byte[] responseBody,
                String retryAfter) throws IOException {
            return new StubServer(status, responseBody, retryAfter);
        }

        URI endpoint() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat");
        }

        private void respond(
                HttpExchange exchange, int status,
                byte[] responseBody, String retryAfter)
                throws IOException {
            requestCount.incrementAndGet();
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            if (retryAfter != null) {
                exchange.getResponseHeaders().set("Retry-After", retryAfter);
            }
            exchange.sendResponseHeaders(status, responseBody.length);
            try (HttpExchange closable = exchange;
                 java.io.OutputStream output = exchange.getResponseBody()) {
                output.write(responseBody);
            }
        }

        @Override public void close() {
            server.stop(0);
        }
    }
}
