package com.portfolio.agent.infrastructure.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.DiagnosticLevel;
import com.portfolio.agent.turn.execution.TurnDeadline;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

public final class OpenAiCompatibleStructuredModelTransport implements StructuredModelTransport {
    static final int MAX_RESPONSE_BYTES = 256 * 1024;
    static final int DEFAULT_RATE_LIMIT_RETRY_AFTER_SECONDS = 30;
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final Duration operationTimeout;
    private final DiagnosticEventPublisher diagnostics;
    private final Function<ModelTransportBinding, URI> endpointResolver;

    public OpenAiCompatibleStructuredModelTransport(
            HttpClient client, ObjectMapper mapper,
            Duration operationTimeout, DiagnosticEventPublisher diagnostics) {
        this(client, mapper, operationTimeout, diagnostics,
                ModelTransportBinding::getEndpoint);
    }

    OpenAiCompatibleStructuredModelTransport(
            HttpClient client, ObjectMapper mapper,
            Duration operationTimeout,
            DiagnosticEventPublisher diagnostics,
            Function<ModelTransportBinding, URI> endpointResolver) {
        this.client = java.util.Objects.requireNonNull(client, "client");
        this.mapper = java.util.Objects.requireNonNull(mapper, "mapper");
        this.operationTimeout = java.util.Objects.requireNonNull(
                operationTimeout, "operationTimeout");
        this.diagnostics = java.util.Objects.requireNonNull(diagnostics, "diagnostics");
        this.endpointResolver = java.util.Objects.requireNonNull(
                endpointResolver, "endpointResolver");
    }

    @Override
    public StructuredModelResponse execute(
            ModelTransportBinding binding, StructuredModelRequest request) {
        ModelTransportBinding resolvedBinding = java.util.Objects.requireNonNull(
                binding, "binding");
        long startedAt = System.nanoTime();
        try {
            TurnDeadline operationDeadline =
                    request.deadline().cappedAt(operationTimeout);
            String requestBody = body(resolvedBinding, request);
            long timeout = operationDeadline.remainingMillis();
            if (timeout < 1) {
                throw new StructuredModelFailure(
                        StructuredModelFailure.Code.DEADLINE_EXCEEDED);
            }
            URI endpoint = java.util.Objects.requireNonNull(
                    endpointResolver.apply(resolvedBinding), "resolved endpoint");
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofMillis(timeout))
                    .header("Content-Type", "application/json")
                    .header("Authorization", resolvedBinding.authorizationHeaderValue())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody)).build();
            CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(
                    httpRequest, limitedByteArrayHandler(MAX_RESPONSE_BYTES));
            HttpResponse<byte[]> response;
            try {
                response = future.get(timeout, TimeUnit.MILLISECONDS);
            } catch (TimeoutException timeoutFailure) {
                future.cancel(true);
                throw new StructuredModelFailure(
                        StructuredModelFailure.Code.DEADLINE_EXCEEDED,
                        timeoutFailure);
            } catch (ExecutionException executionFailure) {
                Throwable cause = executionFailure.getCause();
                if (containsCause(cause, HttpTimeoutException.class)) {
                    throw new StructuredModelFailure(
                            StructuredModelFailure.Code.DEADLINE_EXCEEDED,
                            cause);
                }
                if (containsCause(cause, ResponseTooLargeException.class)) {
                    throw new StructuredModelFailure(
                            StructuredModelFailure.Code.RESPONSE_TOO_LARGE, cause);
                }
                throw new StructuredModelFailure(
                        StructuredModelFailure.Code.TRANSPORT_UNAVAILABLE,
                        cause);
            } catch (CancellationException cancelled) {
                throw new StructuredModelFailure(
                        StructuredModelFailure.Code.TRANSPORT_UNAVAILABLE,
                        cancelled);
            } catch (InterruptedException interrupted) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                throw new StructuredModelFailure(
                        StructuredModelFailure.Code.TRANSPORT_UNAVAILABLE,
                        interrupted);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                StructuredModelFailure.Code code =
                        classifyHttpStatus(response.statusCode());
                Integer retryAfterSeconds = code == StructuredModelFailure.Code.RATE_LIMITED
                        ? retryAfterSeconds(response) : null;
                throw new StructuredModelFailure(code, retryAfterSeconds, null);
            }
            JsonNode root;
            try {
                root = mapper.readTree(response.body());
            } catch (Exception invalidJson) {
                throw new StructuredModelFailure(
                        StructuredModelFailure.Code.RESPONSE_JSON_INVALID, invalidJson);
            }
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.size() != 1) {
                throw new StructuredModelFailure(
                        StructuredModelFailure.Code.RESPONSE_ENVELOPE_INVALID);
            }
            JsonNode content = choices.get(0).path("message").get("content");
            if (content == null || !content.isTextual() || content.textValue().isBlank()) {
                throw new StructuredModelFailure(
                        StructuredModelFailure.Code.RESPONSE_ENVELOPE_INVALID);
            }
            StructuredModelResponse result = new StructuredModelResponse(content.textValue());
            publish(request.operation(), true, null, startedAt);
            return result;
        } catch (StructuredModelFailure failure) {
            publish(request.operation(), false, failure.getCode().name(), startedAt);
            throw failure;
        }
        catch (Exception failure) {
            publish(request.operation(), false,
                    StructuredModelFailure.Code.TRANSPORT_UNAVAILABLE.name(), startedAt);
            throw new StructuredModelFailure(StructuredModelFailure.Code.TRANSPORT_UNAVAILABLE, failure);
        }
    }

    private void publish(String operation, boolean success, String failureCode, long startedAt) {
        try {
            DiagnosticEvent.Builder event = DiagnosticEvent.builder(
                            success ? "provider.call.completed" : "provider.call.failed",
                            success ? DiagnosticLevel.INFO : DiagnosticLevel.WARN)
                    .field("provider.operation", operation)
                    .field("event.outcome", success ? "SUCCESS" : "FAILURE")
                    .field("duration.bucket", durationBucket(startedAt))
                    .field("response.present", success);
            if (failureCode != null) event.field("failure.code", failureCode);
            if (failureCode != null) {
                event.field("failure.layer",
                        StructuredModelFailure.Code.valueOf(failureCode).getLayer());
            }
            diagnostics.publish(event.build());
        } catch (RuntimeException ignored) {
            // Diagnostics never change model behavior.
        }
    }

    private String durationBucket(long startedAt) {
        long millis = (System.nanoTime() - startedAt) / 1_000_000L;
        if (millis < 100) return "LT_100_MS";
        if (millis < 500) return "FROM_100_TO_499_MS";
        if (millis < 2000) return "FROM_500_TO_1999_MS";
        return "GTE_2000_MS";
    }

    private String body(
            ModelTransportBinding binding,
            StructuredModelRequest request) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", binding.getModelName());
        payload.put("messages", List.of(
                Map.of("role", "system", "content", request.systemPrompt()),
                Map.of("role", "user", "content", request.userPrompt())));
        binding.getProtocolProfile().applyStructuredOutputFields(payload);
        payload.put("max_tokens", Math.min(
                request.maxOutputTokens(), binding.getMaxOutputTokens()));
        payload.put("temperature", request.temperature());
        return mapper.writeValueAsString(payload);
    }

    private StructuredModelFailure.Code classifyHttpStatus(int status) {
        if (status == 401 || status == 403) {
            return StructuredModelFailure.Code.AUTHENTICATION_REJECTED;
        }
        if (status == 402) {
            return StructuredModelFailure.Code.BILLING_REJECTED;
        }
        if (status == 429) {
            return StructuredModelFailure.Code.RATE_LIMITED;
        }
        if (status >= 500) {
            return StructuredModelFailure.Code.PROVIDER_UNAVAILABLE;
        }
        return StructuredModelFailure.Code.PROVIDER_REJECTED;
    }

    private int retryAfterSeconds(HttpResponse<?> response) {
        String raw = response.headers().firstValue("Retry-After").orElse(null);
        if (raw == null) {
            return DEFAULT_RATE_LIMIT_RETRY_AFTER_SECONDS;
        }
        try {
            long seconds = Long.parseLong(raw.trim());
            return (int) Math.max(1L, Math.min(300L, seconds));
        } catch (NumberFormatException invalid) {
            return DEFAULT_RATE_LIMIT_RETRY_AFTER_SECONDS;
        }
    }

    private boolean containsCause(Throwable failure, Class<? extends Throwable> type) {
        Throwable current = failure;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            if (current.getCause() == current) {
                return false;
            }
            current = current.getCause();
        }
        return false;
    }

    private HttpResponse.BodyHandler<byte[]> limitedByteArrayHandler(int maxBytes) {
        return responseInfo -> new LimitedByteArraySubscriber(maxBytes);
    }

    private static final class LimitedByteArraySubscriber
            implements HttpResponse.BodySubscriber<byte[]> {
        private final int maxBytes;
        private final ByteArrayOutputStream body;
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;

        private LimitedByteArraySubscriber(int maxBytes) {
            this.maxBytes = maxBytes;
            this.body = new ByteArrayOutputStream(Math.min(maxBytes, 8 * 1024));
        }

        @Override public CompletionStage<byte[]> getBody() { return result; }

        @Override public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > maxBytes - body.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new ResponseTooLargeException());
                    return;
                }
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                body.writeBytes(bytes);
            }
            if (!result.isDone()) {
                subscription.request(1);
            }
        }

        @Override public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override public void onComplete() {
            result.complete(body.toByteArray());
        }
    }

    private static final class ResponseTooLargeException extends RuntimeException { }
}
