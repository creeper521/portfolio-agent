package com.portfolio.agent.infrastructure.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.ModelOutputDiagnostics;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderProtocolProfile;
import com.portfolio.agent.infrastructure.model.provider.ModelRef;
import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.structured.StructuredModelTestFixtures;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputGateway;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeRequest;
import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.turn.infrastructure.model.OpenAiCompatibleGeneralKnowledgeAdapter;
import com.portfolio.agent.turn.planning.UserGoalProposal;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class OpenAiCompatibleStructuredModelTransportDeadlineTest {

    @Test
    void attemptCapWinsOverLongerRequestAndOperationTimeouts()
            throws Exception {
        try (HeaderStallingServer server = new HeaderStallingServer()) {
            OpenAiCompatibleStructuredModelTransport transport = transport(
                    server.endpoint(), Duration.ofSeconds(10));
            ProviderAttemptContext attempt = new ProviderAttemptContext(
                    UUID.randomUUID(), 1, 2, false,
                    Duration.ofMillis(250));

            long startedAt = System.nanoTime();
            StructuredModelFailure failure = catchThrowableOfType(
                    () -> transport.execute(
                            binding(), request(Duration.ofSeconds(10)),
                            attempt),
                    StructuredModelFailure.class);

            assertThat(failure.getCode()).isEqualTo(
                    StructuredModelFailure.Code.DEADLINE_EXCEEDED);
            assertThat(failure.getTimeoutDisposition()).isEqualTo(
                    StructuredModelFailure.TimeoutDisposition.NO_RESPONSE);
            assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
                    .isLessThan(Duration.ofSeconds(2));
            assertThat(server.requestCount).hasValue(1);
        }
    }

    @Test
    void successfulResponseCanCompleteInsideTheAttemptCap()
            throws Exception {
        try (RespondingServer server = new RespondingServer(
                nativeSuccessBody())) {
            OpenAiCompatibleStructuredModelTransport transport = transport(
                    server.endpoint(), Duration.ofSeconds(10));

            StructuredModelResponse response = transport.execute(
                    binding(), request(Duration.ofSeconds(10)),
                    new ProviderAttemptContext(
                            UUID.randomUUID(), 1, 2, false,
                            Duration.ofSeconds(2)));

            assertThat(response).isNotNull();
            assertThat(server.requestCount).hasValue(1);
        }
    }

    @Test
    void defaultTenSecondQwenBudgetReachesTwoAttemptsWithinOneDeadline()
            throws Exception {
        try (FirstHeaderStallThenSuccessServer server =
                     new FirstHeaderStallThenSuccessServer()) {
            List<DiagnosticEvent> events = new ArrayList<>();
            OpenAiCompatibleStructuredModelTransport transport =
                    new OpenAiCompatibleStructuredModelTransport(
                            HttpClient.newHttpClient(), new ObjectMapper(),
                            Duration.ofSeconds(10), events::add,
                            StructuredModelTestFixtures.contracts(),
                            ignored -> server.endpoint());
            OpenAiCompatibleGeneralKnowledgeAdapter adapter =
                    new OpenAiCompatibleGeneralKnowledgeAdapter(
                            new StructuredOutputGateway(
                                    transport,
                                    StructuredModelTestFixtures.contracts()),
                            new ObjectMapper(), "canonical-prompt", 1200,
                            Duration.ofSeconds(10), "draft-prompt",
                            ModelOutputDiagnostics.none());

            long startedAt = System.nanoTime();
            adapter.generate(
                    GeneralKnowledgeRequest.explanation(
                            "并发控制", UserGoalProposal.Depth.CONCISE,
                            GeneralKnowledgeRequest.Audience.GUEST,
                            "public-1", TurnDeadline.after(
                                    Duration.ofSeconds(10),
                                    Clock.systemUTC())),
                    StructuredModelTestFixtures.resolvedModel(
                            StructuredModelTestFixtures
                                    .qwenV7ToolBindings()));
            Duration elapsed = Duration.ofNanos(
                    System.nanoTime() - startedAt);

            assertThat(server.requestCount).hasValue(2);
            assertThat(events).hasSize(2);
            assertThat(events).extracting(event ->
                            event.getFields().get("attempt.index"))
                    .containsExactly(1, 2);
            assertThat(elapsed).isLessThan(Duration.ofSeconds(10));
        }
    }

    @Test
    void qwenGeneralIntegrationRetriesOnlyANoResponseTimeout()
            throws Exception {
        try (HeaderStallingServer server = new HeaderStallingServer()) {
            assertQwenGeneralAttempts(
                    server.endpoint(), server.requestCount, 2);
        }
        try (StallingServer server = new StallingServer()) {
            assertQwenGeneralAttempts(
                    server.endpoint(), server.requestCount, 1);
        }
    }

    @Test
    void refusedConnectionIsClassifiedAsApprovedConnectionFailure() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(
                0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        OpenAiCompatibleStructuredModelTransport transport = transport(
                URI.create("http://127.0.0.1:" + closedPort + "/closed"),
                Duration.ofSeconds(2));

        StructuredModelFailure failure = catchThrowableOfType(
                () -> transport.execute(
                        binding(), request(Duration.ofSeconds(3))),
                StructuredModelFailure.class);

        assertThat(failure.getCode())
                .isEqualTo(StructuredModelFailure.Code.TRANSPORT_UNAVAILABLE);
        assertThat(failure.getTransportDisposition()).isEqualTo(
                StructuredModelFailure.TransportDisposition.CONNECTION);
    }

    @Test
    void noResponseTimeoutIsClassifiedSeparatelyFromABodyStall()
            throws Exception {
        try (HeaderStallingServer server = new HeaderStallingServer()) {
            OpenAiCompatibleStructuredModelTransport transport = transport(
                    server.endpoint(), Duration.ofMillis(250));

            StructuredModelFailure failure = catchThrowableOfType(
                    () -> transport.execute(
                            binding(), request(Duration.ofSeconds(2))),
                    StructuredModelFailure.class);

            assertThat(server.requestStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(failure.getCode()).isEqualTo(
                    StructuredModelFailure.Code.DEADLINE_EXCEEDED);
            assertThat(failure.getTimeoutDisposition()).isEqualTo(
                    StructuredModelFailure.TimeoutDisposition.NO_RESPONSE);
        }
    }

    @Test
    void bodyStallIsCancelledByTheOperationBudget() throws Exception {
        try (StallingServer server = new StallingServer()) {
            OpenAiCompatibleStructuredModelTransport transport = transport(
                    server.endpoint(), Duration.ofMillis(500));
            StructuredModelRequest request = request(Duration.ofSeconds(1));

            long startedAt = System.nanoTime();
            StructuredModelFailure failure = catchThrowableOfType(
                    () -> transport.execute(binding(), request), StructuredModelFailure.class);

            assertThat(failure.getCode())
                    .isEqualTo(StructuredModelFailure.Code.DEADLINE_EXCEEDED);
            assertThat(failure.getTimeoutDisposition()).isEqualTo(
                    StructuredModelFailure.TimeoutDisposition.RESPONSE_STARTED);
            assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
                    .isLessThan(Duration.ofSeconds(2));
            assertThat(server.bodyStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(server.connectionClosed.await(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void interruptionCancelsTheUnderlyingRequestAndRestoresInterruptStatus() throws Exception {
        try (StallingServer server = new StallingServer()) {
            OpenAiCompatibleStructuredModelTransport transport = transport(
                    server.endpoint(), Duration.ofSeconds(5));
            AtomicReference<StructuredModelFailure> outcome = new AtomicReference<>();
            AtomicBoolean interruptRestored = new AtomicBoolean();
            Thread caller = Thread.ofPlatform().start(() -> {
                try {
                    transport.execute(binding(), request(Duration.ofSeconds(5)));
                } catch (StructuredModelFailure failure) {
                    outcome.set(failure);
                    interruptRestored.set(Thread.currentThread().isInterrupted());
                }
            });

            assertThat(server.bodyStarted.await(1, TimeUnit.SECONDS)).isTrue();
            caller.interrupt();
            caller.join(1_000);

            assertThat(caller.isAlive()).isFalse();
            assertThat(outcome.get().getCode())
                    .isEqualTo(StructuredModelFailure.Code.TRANSPORT_UNAVAILABLE);
            assertThat(outcome.get().getTransportDisposition()).isEqualTo(
                    StructuredModelFailure.TransportDisposition.INTERRUPTED);
            assertThat(interruptRestored).isTrue();
            assertThat(server.connectionClosed.await(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    private OpenAiCompatibleStructuredModelTransport transport(
            URI endpoint, Duration operationTimeout) {
        return new OpenAiCompatibleStructuredModelTransport(
                HttpClient.newHttpClient(), new ObjectMapper(),
                operationTimeout, event -> { },
                StructuredModelTestFixtures.contracts(), ignored -> endpoint);
    }

    private void assertQwenGeneralAttempts(
            URI endpoint, AtomicInteger serverCalls, int expectedAttempts) {
        List<DiagnosticEvent> providerEvents = new ArrayList<>();
        OpenAiCompatibleStructuredModelTransport transport =
                new OpenAiCompatibleStructuredModelTransport(
                        HttpClient.newHttpClient(), new ObjectMapper(),
                        Duration.ofMillis(250), providerEvents::add,
                        StructuredModelTestFixtures.contracts(),
                        ignored -> endpoint);
        OpenAiCompatibleGeneralKnowledgeAdapter adapter =
                new OpenAiCompatibleGeneralKnowledgeAdapter(
                        new StructuredOutputGateway(
                                transport,
                                StructuredModelTestFixtures.contracts()),
                        new ObjectMapper(), "canonical-prompt", 1200,
                        Duration.ofSeconds(5), "draft-prompt",
                        ModelOutputDiagnostics.none());

        SelectedModelFailureException failure = catchThrowableOfType(
                () -> adapter.generate(
                        GeneralKnowledgeRequest.explanation(
                                "并发控制", UserGoalProposal.Depth.CONCISE,
                                GeneralKnowledgeRequest.Audience.GUEST,
                                "public-1", TurnDeadline.after(
                                        Duration.ofSeconds(5),
                                        Clock.systemUTC())),
                        StructuredModelTestFixtures.resolvedModel(
                                StructuredModelTestFixtures
                                        .qwenV7ToolBindings())),
                SelectedModelFailureException.class);

        assertThat(failure.getCode()).isEqualTo(
                SelectedModelFailureException.Code
                        .SELECTED_MODEL_TEMPORARILY_UNAVAILABLE);
        assertThat(serverCalls).hasValue(expectedAttempts);
        assertThat(providerEvents).hasSize(expectedAttempts);
        assertThat(providerEvents).extracting(event ->
                        event.getFields().get("attempt.index"))
                .containsExactlyElementsOf(
                        expectedAttempts == 2
                                ? List.of(1, 2) : List.of(1));
        if (expectedAttempts == 2) {
            assertThat(providerEvents.get(1).getFields())
                    .containsEntry("duplicate.billing.risk", true);
        }
    }

    private ModelTransportBinding binding() {
        return new ModelTransportBinding(
                ModelRef.of("glm"), "0".repeat(64),
                URI.create("https://example.test/chat"),
                "glm-4.7-flash",
                ModelProviderProtocolProfile.ZHIPU_CHAT_COMPLETIONS,
                "test-key", 32, StructuredModelTestFixtures.nativeBindings());
    }

    private StructuredModelRequest request(Duration timeout) {
        return new StructuredModelRequest(
                ModelOperation.GENERAL_KNOWLEDGE, "system", "user", 32, 0.0d,
                TurnDeadline.after(timeout, Clock.systemUTC()));
    }

    private static final class StallingServer implements AutoCloseable {
        private final AtomicInteger requestCount = new AtomicInteger();
        private final CountDownLatch bodyStarted = new CountDownLatch(1);
        private final CountDownLatch connectionClosed = new CountDownLatch(1);
        private final AtomicBoolean stopping = new AtomicBoolean();
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final HttpServer server;

        private StallingServer() throws IOException {
            server = HttpServer.create(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.setExecutor(executor);
            server.createContext("/stall", exchange -> {
                requestCount.incrementAndGet();
                exchange.sendResponseHeaders(200, 0);
                try (OutputStream body = exchange.getResponseBody()) {
                    body.write('{');
                    body.flush();
                    bodyStarted.countDown();
                    while (!stopping.get()) {
                        body.write(' ');
                        body.flush();
                        Thread.sleep(10);
                    }
                } catch (IOException disconnected) {
                    connectionClosed.countDown();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
            server.start();
        }

        private URI endpoint() {
            return URI.create("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/stall");
        }

        @Override
        public void close() {
            stopping.set(true);
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private static final class HeaderStallingServer implements AutoCloseable {
        private final AtomicInteger requestCount = new AtomicInteger();
        private final CountDownLatch requestStarted = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final HttpServer server;

        private HeaderStallingServer() throws IOException {
            server = HttpServer.create(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.setExecutor(executor);
            server.createContext("/no-response", exchange -> {
                requestCount.incrementAndGet();
                requestStarted.countDown();
                try (exchange) {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
            server.start();
        }

        private URI endpoint() {
            return URI.create("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/no-response");
        }

        @Override
        public void close() {
            release.countDown();
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private static byte[] nativeSuccessBody() {
        return """
                {"choices":[{"finish_reason":"stop","message":
                {"content":"{}"}}]}
                """.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] qwenDraftSuccessBody() {
        return """
                {"choices":[{"finish_reason":"tool_calls","message":{
                "content":null,"tool_calls":[{"type":"function","function":{
                "name":"emit_general_provider_draft_v4",
                "arguments":"{\\"definition\\":\\"定义。\\",\\"mechanism\\":\\"机制。\\",\\"caveats\\":[]}"}}]}}]}
                """.getBytes(StandardCharsets.UTF_8);
    }

    private static class RespondingServer implements AutoCloseable {
        private final AtomicInteger requestCount = new AtomicInteger();
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final HttpServer server;

        private RespondingServer(byte[] response) throws IOException {
            server = HttpServer.create(
                    new InetSocketAddress(
                            InetAddress.getLoopbackAddress(), 0), 0);
            server.setExecutor(executor);
            server.createContext("/success", exchange -> {
                requestCount.incrementAndGet();
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream body = exchange.getResponseBody()) {
                    body.write(response);
                }
            });
            server.start();
        }

        protected URI endpoint() {
            return URI.create("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/success");
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private static final class FirstHeaderStallThenSuccessServer
            implements AutoCloseable {
        private final AtomicInteger requestCount = new AtomicInteger();
        private final CountDownLatch release = new CountDownLatch(1);
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final HttpServer server;

        private FirstHeaderStallThenSuccessServer() throws IOException {
            server = HttpServer.create(
                    new InetSocketAddress(
                            InetAddress.getLoopbackAddress(), 0), 0);
            server.setExecutor(executor);
            server.createContext("/sequence", exchange -> {
                int attempt = requestCount.incrementAndGet();
                if (attempt == 1) {
                    try (exchange) {
                        release.await(12, TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    return;
                }
                byte[] response = qwenDraftSuccessBody();
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream body = exchange.getResponseBody()) {
                    body.write(response);
                }
            });
            server.start();
        }

        private URI endpoint() {
            return URI.create("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/sequence");
        }

        @Override
        public void close() {
            release.countDown();
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
