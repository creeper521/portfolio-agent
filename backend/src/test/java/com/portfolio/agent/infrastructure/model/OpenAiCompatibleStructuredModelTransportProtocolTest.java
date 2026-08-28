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
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeRequest;
import com.portfolio.agent.turn.infrastructure.model.OpenAiCompatibleGeneralKnowledgeAdapter;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class OpenAiCompatibleStructuredModelTransportProtocolTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @ParameterizedTest
    @EnumSource(value = ModelOperation.class, names = {
            "TURN_INTERPRETATION", "GENERAL_KNOWLEDGE"
    })
    void secretLikeOutboundIsRejectedBeforeHttpAndProviderCallDiagnostics(
            ModelOperation operation) throws Exception {
        String marker = "api_key=synthetic-certification-marker";
        try (StubServer server = StubServer.responding(
                200, successBody("qwen3.7-flash"))) {
            List<DiagnosticEvent> events = new ArrayList<>();

            StructuredModelFailure failure = catchThrowableOfType(
                    () -> transport(server.endpoint(), events::add).execute(
                            binding("qwen", "qwen3.7-flash",
                                    ModelProviderProtocolProfile
                                            .DASHSCOPE_CHAT_COMPLETIONS),
                            request(operation, "system JSON",
                                    "general request " + marker)),
                    StructuredModelFailure.class);

            assertThat(failure).isNotNull();
            assertThat(failure.getCode().name())
                    .isEqualTo("OUTBOUND_SECRET_LIKE_REJECTED");
            assertThat(failure.getReason()).isNotNull();
            assertThat(failure.getReason().name())
                    .isEqualTo("SECRET_LIKE_CONTENT");
            assertThat(server.requestCount).hasValue(0);
            assertThat(events).noneMatch(event ->
                    event.getName().startsWith("provider.call."));
            assertThat(failure.toString()).doesNotContain(marker);
            assertThat(events.toString()).doesNotContain(marker);
        }
    }

    @ParameterizedTest
    @MethodSource("closedSecretLikeContent")
    void closedSecretLikeContentIsRejectedBeforeHttp(String content)
            throws Exception {
        try (StubServer server = StubServer.responding(
                200, successBody("qwen3.7-flash"))) {
            List<DiagnosticEvent> events = new ArrayList<>();

            StructuredModelFailure failure = catchThrowableOfType(
                    () -> transport(server.endpoint(), events::add).execute(
                            binding("qwen", "qwen3.7-flash",
                                    ModelProviderProtocolProfile
                                            .DASHSCOPE_CHAT_COMPLETIONS),
                            request(ModelOperation.GENERAL_KNOWLEDGE,
                                    "system JSON", content)),
                    StructuredModelFailure.class);

            assertThat(failure).isNotNull();
            assertThat(failure.getCode().name())
                    .isEqualTo("OUTBOUND_SECRET_LIKE_REJECTED");
            assertThat(failure.getReason().name())
                    .isEqualTo("SECRET_LIKE_CONTENT");
            assertThat(server.requestCount).hasValue(0);
            assertThat(events).noneMatch(event ->
                    event.getName().startsWith("provider.call."));
            assertThat(failure.toString()).doesNotContain(content);
            assertThat(events.toString()).doesNotContain(content);
        }
    }

    @ParameterizedTest
    @MethodSource("ordinaryOrPlaceholderContent")
    void ordinaryTermsAndPlaceholdersAreNotRejected(String content)
            throws Exception {
        try (StubServer server = StubServer.responding(
                200, successBody("qwen3.7-flash"))) {
            List<DiagnosticEvent> events = new ArrayList<>();
            transport(server.endpoint(), events::add).execute(
                    binding("qwen", "qwen3.7-flash",
                            ModelProviderProtocolProfile
                                    .DASHSCOPE_CHAT_COMPLETIONS),
                    request(ModelOperation.GENERAL_KNOWLEDGE,
                            "system JSON", content));

            assertThat(server.requestCount).hasValue(1);
            assertThat(events).singleElement().satisfies(event ->
                    assertThat(event.getName())
                            .isEqualTo("provider.call.completed"));
        }
    }

    @Test
    void ordinaryApiKeyQuestionIsNotRejectedAsSecretLikeContent()
            throws Exception {
        try (StubServer server = StubServer.responding(
                200, successBody("qwen3.7-flash"))) {
            List<DiagnosticEvent> events = new ArrayList<>();

            transport(server.endpoint(), events::add).execute(
                    binding("qwen", "qwen3.7-flash",
                            ModelProviderProtocolProfile
                                    .DASHSCOPE_CHAT_COMPLETIONS),
                    request(ModelOperation.GENERAL_KNOWLEDGE,
                            "system JSON", "什么是 API key？"));

            assertThat(server.requestCount).hasValue(1);
            assertThat(events).singleElement().satisfies(event ->
                    assertThat(event.getName())
                            .isEqualTo("provider.call.completed"));
        }
    }

    @Test
    void serverOwnedCredentialIsOnlySentInAuthorizationHeader()
            throws Exception {
        String credential = "server-owned-credential-sentinel";
        ModelTransportBinding binding = new ModelTransportBinding(
                ModelRef.of("qwen"), "0".repeat(64),
                URI.create("https://example.test/chat"),
                "qwen3.7-flash",
                ModelProviderProtocolProfile.DASHSCOPE_CHAT_COMPLETIONS,
                credential, 32, StructuredModelTestFixtures.nativeBindings());
        try (StubServer server = StubServer.responding(
                200, successBody("qwen3.7-flash"))) {
            transport(server.endpoint(), event -> { }).execute(binding, request());

            assertThat(server.authorization.get())
                    .isEqualTo("Bearer " + credential);
            assertThat(server.requestBody.get()).doesNotContain(credential);
        }
    }

    private static Stream<String> closedSecretLikeContent() {
        return Stream.of(
                "api_key=live-synthetic-value-001",
                "Authorization: Bearer bearer-synthetic-value-002",
                "Cookie: session=synthetic-cookie-value-003",
                "db_password=synthetic-db-password-004",
                "internal_token=synthetic-internal-token-005",
                "access_token=synthetic-access-token-006",
                "refresh_token=synthetic-refresh-token-007",
                "client_secret=synthetic-client-secret-008",
                "credential=synthetic-credential-009",
                "OPENAI_API_KEY=synthetic-openai-key-010",
                "{\"api_key\":\"synthetic-json-key-011\"}",
                "Authorization: Basic QWxhZGRpbjpvcGVuLXNlc2FtZQ==",
                "Authorization: Bearer synthetic-bearer-token-012",
                "WEBHOOK_SECRET=synthetic-webhook-secret-013",
                "DATABASE_PASSWORD=correcthorsebatterystaple",
                "SESSION_TOKEN=synthetic-session-token-014",
                "password=synthetic-generic-password-015",
                "secret=synthetic-generic-secret-016",
                "token=synthetic-generic-token-017",
                "password=\"correct horse battery staple 2026\"",
                "token=[SyntheticTokenValue12345]",
                "api_key=<correcthorsebatterystaple",
                "secret=(SyntheticSecretValue12345)",
                "api_key=<token>synthetic-real-secret-12345",
                "client_secret=${CLIENT_SECRET}synthetic-secret-material-123",
                "api_key=<token",
                "api_key=<",
                "password=\"required-for-authentication",
                "password=\"",
                "client_secret=${CLIENT_SECRET",
                "{\"client_secret\":\"${CLIENT_SECRET}synthetic-secret-material-123\"}",
                "{\"password\":\"'${DATABASE_PASSWORD}'\"}",
                "sk-1234567890abcdefghijklmnop",
                "0123456789abcdef0123456789abcdef.Abcdefghijklmnop",
                "-----BEGIN PRIVATE KEY-----");
    }

    private static Stream<String> ordinaryOrPlaceholderContent() {
        return Stream.of(
                "什么是 API key？",
                "Authorization、Cookie 和 access token 有什么区别？",
                "数据库 password 和 client secret 应该如何轮换？",
                "API key: required",
                "api_key=optional",
                "credential=authentication",
                "client_secret=configured",
                "api_key=required-for-authentication",
                "OPENAI_API_KEY=optional-when-configured",
                "{\"password\":\"authentication-required\"}",
                "token=not-configured-yet",
                "api_key=your_api_key_here",
                "Authorization: Bearer required-for-authentication",
                "token=[REDACTED]",
                "api_key=<token>",
                "secret=(placeholder)",
                "password={not-configured-yet}",
                "{\"client_secret\":\"${CLIENT_SECRET}\"}",
                "{\"password\":\"${DATABASE_PASSWORD}\"}",
                "api_key=<token>, documentation placeholder",
                "client_secret=${CLIENT_SECRET}; rotation documentation",
                "api_key=",
                "api_key=your_api_key",
                "Authorization: Bearer <token>",
                "client_secret=${CLIENT_SECRET}",
                "credential=REDACTED");
    }

    @ParameterizedTest
    @MethodSource("invalidResponseModels")
    void responseModelIdentityIsRequiredTypedAndEqualWithoutValueLeakage(
            String body, String expectedReason) throws Exception {
        String privateWrongModel = "private-wrong-model-sentinel";
        try (StubServer server = StubServer.responding(
                200, body.replace("WRONG_MODEL", privateWrongModel)
                        .getBytes(StandardCharsets.UTF_8))) {
            List<DiagnosticEvent> events = new ArrayList<>();

            StructuredModelFailure failure = catchThrowableOfType(
                    () -> transport(server.endpoint(), events::add).execute(
                            binding("glm", "glm-4.7-flash",
                                    ModelProviderProtocolProfile
                                            .ZHIPU_CHAT_COMPLETIONS),
                            request()),
                    StructuredModelFailure.class);

            assertThat(failure).isNotNull();
            assertThat(failure.getCode()).isEqualTo(
                    StructuredModelFailure.Code.RESPONSE_ENVELOPE_INVALID);
            assertThat(failure.getReason().name()).isEqualTo(expectedReason);
            assertThat(server.requestCount).hasValue(1);
            assertThat(events).singleElement().satisfies(event -> {
                assertThat(event.getName()).isEqualTo("provider.call.failed");
                assertThat(event.getFields().get("failure.reason"))
                        .isEqualTo(expectedReason);
                assertThat(event.toString()).doesNotContain(privateWrongModel);
            });
            assertThat(failure.toString()).doesNotContain(privateWrongModel);
        }
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments>
            invalidResponseModels() {
        String choices = "\"choices\":[{\"finish_reason\":\"stop\"," +
                "\"message\":{\"content\":" +
                "\"{\\\"answer\\\":\\\"ok\\\"}\"}}]";
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(
                        "{" + choices + "}", "MODEL_REQUIRED"),
                org.junit.jupiter.params.provider.Arguments.of(
                        "{\"model\":7," + choices + "}", "MODEL_TYPE"),
                org.junit.jupiter.params.provider.Arguments.of(
                        "{\"model\":\"WRONG_MODEL\"," + choices + "}",
                        "MODEL_MISMATCH"));
    }

    @ParameterizedTest
    @MethodSource("validResponseModels")
    void responseModelIdentityMatchesTheFrozenQwenOrGlmBinding(
            String modelRef, String modelName,
            ModelProviderProtocolProfile profile) throws Exception {
        try (StubServer server = StubServer.responding(
                200, successBody(modelName))) {
            StructuredModelResponse response = transport(
                    server.endpoint(), event -> { }).execute(
                    binding(modelRef, modelName, profile), request());

            assertThat(response).isNotNull();
            assertThat(server.requestCount).hasValue(1);
        }
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments>
            validResponseModels() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(
                        "qwen", "qwen3.7-flash",
                        ModelProviderProtocolProfile
                                .DASHSCOPE_CHAT_COMPLETIONS),
                org.junit.jupiter.params.provider.Arguments.of(
                        "glm", "glm-4.7-flash",
                        ModelProviderProtocolProfile
                                .ZHIPU_CHAT_COMPLETIONS));
    }

    @Test
    void generalResponseModelMismatchIsAttemptedAndNeverRetried()
            throws Exception {
        try (StubServer server = StubServer.responding(
                200, successBody("different-model"))) {
            OpenAiCompatibleGeneralKnowledgeAdapter adapter =
                    new OpenAiCompatibleGeneralKnowledgeAdapter(
                            StructuredModelTestFixtures.gateway(
                                    transport(server.endpoint(), event -> { })),
                            MAPPER, "system", 1200, Duration.ofSeconds(10));
            ResolvedModelExecution execution =
                    StructuredModelTestFixtures.resolvedModel(
                            StructuredModelTestFixtures.qwenV7ToolBindings());

            SelectedModelFailureException failure = catchThrowableOfType(
                    () -> adapter.generate(
                            GeneralKnowledgeRequest.explanation(
                                    "并发控制",
                                    UserGoalProposal.Depth.STANDARD,
                                    GeneralKnowledgeRequest.Audience.GUEST,
                                    "public-1", TurnDeadline.after(
                                            Duration.ofSeconds(12),
                                            Clock.systemUTC())),
                            execution),
                    SelectedModelFailureException.class);

            assertThat(failure.getCode()).isEqualTo(
                    SelectedModelFailureException.Code
                            .SELECTED_MODEL_INVALID_RESPONSE);
            assertThat(failure.isAttempted()).isTrue();
            assertThat(server.requestCount).hasValue(1);
            assertThat(execution.wasAttempted(
                    ResolvedModelExecution.Stage.ANSWER_GENERATION)).isTrue();
        }
    }

    @Test
    void zhipuUsesOnlyItsClosedThinkingShapeAndFixedAuthorizationHeader() throws Exception {
        try (StubServer server = StubServer.responding(
                200, successBody("glm-4.7-flash"))) {
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
        byte[] response = successBodyWithUsage(
                "qwen3.7-flash", 0, 256, 65_000);
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
            byte[] response = successBodyWithUsage(
                    "qwen3.7-flash", usageBody);
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
        try (StubServer server = StubServer.responding(
                200, successBody("qwen3.7-flash"))) {
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
        try (StubServer server = StubServer.responding(
                200, successBody("glm-4.7-flash"))) {
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
        try (StubServer server = StubServer.responding(
                200, toolSuccessBody("glm-4.7-flash"))) {
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
        byte[] body = new String(
                toolSuccessBody("glm-4.7-flash"), StandardCharsets.UTF_8)
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
        try (StubServer server = StubServer.responding(
                200, successBody("qwen3.7-flash"))) {
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
                "{\"model\":\"glm-4.7-flash\",\"choices\":[{"
                        + "\"finish_reason\":\"stop\",\"message\":"
                        + "{\"content\":\"{}\",\"tool_calls\":[]}}]}",
                StructuredModelFailure.Code.RESPONSE_ENVELOPE_INVALID, "ENVELOPE");
    }

    @Test
    void oneStatelessTransportExecutesEachExplicitBindingWithoutLeakingPriorSelection()
            throws Exception {
        try (StubServer glmServer = StubServer.responding(
                    200, successBody("glm-4.7-flash"));
             StubServer qwenServer = StubServer.responding(
                    200, successBody("qwen3.7-flash"))) {
            OpenAiCompatibleStructuredModelTransport transport =
                    new OpenAiCompatibleStructuredModelTransport(
                            HttpClient.newHttpClient(), MAPPER,
                            Duration.ofSeconds(2), event -> { },
                            StructuredModelTestFixtures.contracts(), binding ->
                            binding.getModelRef().equals(ModelRef.of("glm"))
                                    ? glmServer.endpoint()
                                    : qwenServer.endpoint());

            transport.execute(binding("glm", "glm-4.7-flash",
                    ModelProviderProtocolProfile.ZHIPU_CHAT_COMPLETIONS), request());
            String glmPayload = glmServer.requestBody.get();
            transport.execute(binding("qwen", "qwen3.7-flash",
                    ModelProviderProtocolProfile.DASHSCOPE_CHAT_COMPLETIONS), request());
            String qwenPayload = qwenServer.requestBody.get();

            assertThat(MAPPER.readTree(glmPayload).path("model").textValue())
                    .isEqualTo("glm-4.7-flash");
            assertThat(MAPPER.readTree(qwenPayload).path("model").textValue())
                    .isEqualTo("qwen3.7-flash");
            assertThat(MAPPER.readTree(glmPayload).has("enable_thinking")).isFalse();
            assertThat(MAPPER.readTree(qwenPayload).has("thinking")).isFalse();
            assertThat(glmServer.requestCount).hasValue(1);
            assertThat(qwenServer.requestCount).hasValue(1);
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
                "{\"model\":\"glm-4.7-flash\",\"choices\":[{"
                        + "\"message\":{\"content\":\"\"}}]}",
                StructuredModelFailure.Code.RESPONSE_ENVELOPE_INVALID, "ENVELOPE");
        assertResponseFailure(
                "{\"model\":\"glm-4.7-flash\",\"choices\":[{"
                        + "\"message\":{\"content\":\"a\"}},"
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
             StubServer otherServer = StubServer.responding(
                     200, successBody(other.getModelName()))) {
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
        return request(ModelOperation.GENERAL_KNOWLEDGE,
                "system JSON", "user");
    }

    private StructuredModelRequest request(
            ModelOperation operation, String systemPrompt, String userPrompt) {
        return new StructuredModelRequest(
                operation, systemPrompt, userPrompt, 64, 0.0d,
                TurnDeadline.after(Duration.ofSeconds(3), Clock.systemUTC()));
    }

    private static byte[] successBody(String model) {
        return ("{\"model\":\"" + model + "\","
                + "\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{\\\"answer\\\":\\\"ok\\\"}\"}}]}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] successBodyWithUsage(
            String model, long promptTokens,
            long completionTokens, long totalTokens) {
        return successBodyWithUsage(
                model,
                "{\"prompt_tokens\":" + promptTokens
                        + ",\"completion_tokens\":" + completionTokens
                        + ",\"total_tokens\":" + totalTokens + "}");
    }

    private static byte[] successBodyWithUsage(String model, String usage) {
        return ("{\"model\":\"" + model + "\","
                + "\"choices\":[{\"finish_reason\":\"stop\",\"message\":"
                + "{\"content\":\"{\\\"answer\\\":\\\"ok\\\"}\"}}],"
                + "\"usage\":" + usage + "}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] toolSuccessBody(String model) {
        return ("""
                {"model":"%s","choices":[{"finish_reason":"tool_calls","message":{
                  "tool_calls":[{"type":"function","function":{
                    "name":"emit_general_draft",
                    "arguments":"{\\"general\\":\\"ok\\"}"
                  }}]
                }}]}
                """.formatted(model)).getBytes(StandardCharsets.UTF_8);
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
