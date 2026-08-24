package com.portfolio.agent.infrastructure.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderKind;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderProtocolProfile;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderRegistrySnapshot;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class OpenAiCompatibleStructuredModelTransportProtocolTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void providerProfilesAreIndependentAndProduceTheirApprovedPayloads() throws Exception {
        assertThat(ModelProviderProtocolProfile.forProvider(ModelProviderKind.DEEPSEEK_V4_FLASH))
                .isEqualTo(ModelProviderProtocolProfile.DEEPSEEK_CHAT_COMPLETIONS_V1);
        assertThat(ModelProviderProtocolProfile.forProvider(ModelProviderKind.GLM_4_7))
                .isEqualTo(ModelProviderProtocolProfile.GLM_CHAT_COMPLETIONS_V1);

        for (ModelProviderKind provider : ModelProviderKind.values()) {
            try (StubServer server = StubServer.responding(200, successBody())) {
                transport(provider, server.endpoint(), event -> { }).execute(request());

                assertThat(server.requestCount).hasValue(1);
                JsonNode payload = MAPPER.readTree(server.requestBody.get());
                assertThat(payload.path("model").textValue()).isEqualTo(
                        ModelProviderRegistrySnapshot.builtIn()
                                .getRequiredDescriptor(provider).getModelName());
                assertThat(payload.path("response_format").path("type").textValue())
                        .isEqualTo("json_object");
                assertThat(payload.path("thinking").path("type").textValue())
                        .isEqualTo("disabled");
                assertThat(payload.path("stream").booleanValue()).isFalse();
                assertThat(payload.properties()).extracting(java.util.Map.Entry::getKey)
                        .containsExactlyInAnyOrder(
                                "model", "messages", "response_format", "thinking",
                                "stream", "max_tokens", "temperature");
            }
        }
    }

    @Test
    void httpFailuresHaveStableCategoriesWithoutRetainingProviderBody() throws Exception {
        assertHttpFailure(401, StructuredModelFailure.Code.AUTHENTICATION_REJECTED);
        assertHttpFailure(403, StructuredModelFailure.Code.AUTHENTICATION_REJECTED);
        assertHttpFailure(429, StructuredModelFailure.Code.RATE_LIMITED);
        assertHttpFailure(500, StructuredModelFailure.Code.PROVIDER_UNAVAILABLE);
        assertHttpFailure(503, StructuredModelFailure.Code.PROVIDER_UNAVAILABLE);
        assertHttpFailure(400, StructuredModelFailure.Code.PROVIDER_REJECTED);
    }

    @Test
    void malformedJsonAndInvalidEnvelopeAreSeparatedInDiagnostics() throws Exception {
        assertResponseFailure("not-json", StructuredModelFailure.Code.RESPONSE_JSON_INVALID, "JSON");
        assertResponseFailure("{}", StructuredModelFailure.Code.RESPONSE_ENVELOPE_INVALID, "ENVELOPE");
    }

    @Test
    void responseBodyIsRejectedAtTheHardByteLimit() throws Exception {
        byte[] oversized = new byte[OpenAiCompatibleStructuredModelTransport.MAX_RESPONSE_BYTES + 1];
        try (StubServer server = StubServer.responding(200, oversized)) {
            List<DiagnosticEvent> events = new ArrayList<>();
            StructuredModelFailure failure = catchThrowableOfType(
                    () -> transport(ModelProviderKind.DEEPSEEK_V4_FLASH,
                            server.endpoint(), events::add).execute(request()),
                    StructuredModelFailure.class);

            assertThat(failure.getCode()).isEqualTo(StructuredModelFailure.Code.RESPONSE_TOO_LARGE);
            assertThat(events).anySatisfy(event -> {
                assertThat(event.getFields().get("failure.code")).isEqualTo("RESPONSE_TOO_LARGE");
                assertThat(event.getFields().get("failure.layer")).isEqualTo("TRANSPORT");
            });
        }
    }

    private void assertHttpFailure(int status, StructuredModelFailure.Code expected) throws Exception {
        String sentinel = "provider-secret-body-must-not-be-retained";
        try (StubServer server = StubServer.responding(status,
                sentinel.getBytes(StandardCharsets.UTF_8))) {
            List<DiagnosticEvent> events = new ArrayList<>();
            StructuredModelFailure failure = catchThrowableOfType(
                    () -> transport(ModelProviderKind.GLM_4_7,
                            server.endpoint(), events::add).execute(request()),
                    StructuredModelFailure.class);

            assertThat(failure.getCode()).isEqualTo(expected);
            assertThat(failure).hasMessage(expected.name());
            assertThat(server.requestCount)
                    .as("HTTP rejection must not retry or switch provider").hasValue(1);
            assertThat(MAPPER.readTree(server.requestBody.get()).path("model").textValue())
                    .isEqualTo(ModelProviderRegistrySnapshot.builtIn()
                            .getRequiredDescriptor(ModelProviderKind.GLM_4_7).getModelName());
            assertThat(events.toString()).doesNotContain(sentinel);
        }
    }

    private void assertResponseFailure(
            String body, StructuredModelFailure.Code expected, String layer) throws Exception {
        try (StubServer server = StubServer.responding(200,
                body.getBytes(StandardCharsets.UTF_8))) {
            List<DiagnosticEvent> events = new ArrayList<>();
            StructuredModelFailure failure = catchThrowableOfType(
                    () -> transport(ModelProviderKind.DEEPSEEK_V4_FLASH,
                            server.endpoint(), events::add).execute(request()),
                    StructuredModelFailure.class);

            assertThat(failure.getCode()).isEqualTo(expected);
            assertThat(server.requestCount)
                    .as("invalid response must not trigger schema repair").hasValue(1);
            assertThat(events).anySatisfy(event -> {
                assertThat(event.getFields().get("failure.code")).isEqualTo(expected.name());
                assertThat(event.getFields().get("failure.layer")).isEqualTo(layer);
            });
        }
    }

    private OpenAiCompatibleStructuredModelTransport transport(
            ModelProviderKind provider, URI endpoint,
            com.portfolio.agent.common.observability.DiagnosticEventPublisher diagnostics) {
        return new OpenAiCompatibleStructuredModelTransport(
                HttpClient.newHttpClient(), MAPPER,
                ModelProviderRegistrySnapshot.builtIn().getRequiredDescriptor(provider),
                "test-key", Duration.ofSeconds(2), diagnostics, endpoint);
    }

    private StructuredModelRequest request() {
        return new StructuredModelRequest(
                "GENERAL_KNOWLEDGE", "system JSON", "user", 64, 0.0d,
                TurnDeadline.after(Duration.ofSeconds(3), Clock.systemUTC()));
    }

    private static byte[] successBody() {
        return "{\"choices\":[{\"message\":{\"content\":\"{\\\"answer\\\":\\\"ok\\\"}\"}}]}"
                .getBytes(StandardCharsets.UTF_8);
    }

    private static final class StubServer implements AutoCloseable {
        private final AtomicReference<String> requestBody = new AtomicReference<>();
        private final AtomicInteger requestCount = new AtomicInteger();
        private final HttpServer server;

        private StubServer(int status, byte[] responseBody) throws IOException {
            server = HttpServer.create(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/chat", exchange -> respond(exchange, status, responseBody));
            server.start();
        }

        static StubServer responding(int status, byte[] responseBody) throws IOException {
            return new StubServer(status, responseBody);
        }

        URI endpoint() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat");
        }

        private void respond(HttpExchange exchange, int status, byte[] responseBody)
                throws IOException {
            requestCount.incrementAndGet();
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(status, responseBody.length);
            try (exchange; var output = exchange.getResponseBody()) {
                output.write(responseBody);
            }
        }

        @Override public void close() {
            server.stop(0);
        }
    }
}
