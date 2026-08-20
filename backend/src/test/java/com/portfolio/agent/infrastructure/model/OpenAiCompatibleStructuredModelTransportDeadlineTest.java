package com.portfolio.agent.infrastructure.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderKind;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderRegistrySnapshot;
import com.portfolio.agent.turn.execution.TurnDeadline;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class OpenAiCompatibleStructuredModelTransportDeadlineTest {

    @Test
    void bodyStallIsCancelledByTheOperationBudget() throws Exception {
        try (StallingServer server = new StallingServer()) {
            OpenAiCompatibleStructuredModelTransport transport = transport(
                    server.endpoint(), Duration.ofMillis(150));
            StructuredModelRequest request = request(Duration.ofMillis(300));

            long startedAt = System.nanoTime();
            StructuredModelFailure failure = catchThrowableOfType(
                    () -> transport.execute(request), StructuredModelFailure.class);

            assertThat(failure.getCode())
                    .isEqualTo(StructuredModelFailure.Code.DEADLINE_EXCEEDED);
            assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
                    .isLessThan(Duration.ofSeconds(1));
            assertThat(server.bodyStarted.await(100, TimeUnit.MILLISECONDS)).isTrue();
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
                    transport.execute(request(Duration.ofSeconds(5)));
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
            assertThat(interruptRestored).isTrue();
            assertThat(server.connectionClosed.await(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    private OpenAiCompatibleStructuredModelTransport transport(
            URI endpoint, Duration operationTimeout) {
        return new OpenAiCompatibleStructuredModelTransport(
                HttpClient.newHttpClient(), new ObjectMapper(),
                ModelProviderRegistrySnapshot.builtIn().getRequiredDescriptor(
                        ModelProviderKind.DEEPSEEK_V4_FLASH),
                "test-key", operationTimeout, event -> { }, endpoint);
    }

    private StructuredModelRequest request(Duration timeout) {
        return new StructuredModelRequest(
                "GENERAL_KNOWLEDGE", "system", "user", 32, 0.0d,
                TurnDeadline.after(timeout, Clock.systemUTC()));
    }

    private static final class StallingServer implements AutoCloseable {
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
}
