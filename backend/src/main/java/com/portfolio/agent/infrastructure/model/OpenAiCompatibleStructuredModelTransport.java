package com.portfolio.agent.infrastructure.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderDescriptor;
import com.portfolio.agent.infrastructure.model.provider.ModelProviderProtocolProfile;
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

public final class OpenAiCompatibleStructuredModelTransport implements StructuredModelTransport {
    static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final ModelProviderDescriptor provider;
    private final String apiKey;
    private final Duration operationTimeout;
    private final DiagnosticEventPublisher diagnostics;
    private final URI endpoint;

    public OpenAiCompatibleStructuredModelTransport(
            HttpClient client, ObjectMapper mapper, ModelProviderDescriptor provider,
            String apiKey, Duration operationTimeout, DiagnosticEventPublisher diagnostics) {
        this(client, mapper, provider, apiKey, operationTimeout, diagnostics,
                provider.getEndpoint());
    }

    OpenAiCompatibleStructuredModelTransport(
            HttpClient client, ObjectMapper mapper, ModelProviderDescriptor provider,
            String apiKey, Duration operationTimeout,
            DiagnosticEventPublisher diagnostics, URI endpoint) {
        this.client = client; this.mapper = mapper; this.provider = provider;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.operationTimeout = operationTimeout;
        this.diagnostics = diagnostics;
        this.endpoint = endpoint;
    }

    @Override public StructuredModelResponse execute(StructuredModelRequest request) {
        long startedAt = System.nanoTime();
        try {
            TurnDeadline operationDeadline =
                    request.deadline().cappedAt(operationTimeout);
            String requestBody = body(request);
            long timeout = operationDeadline.remainingMillis();
            if (timeout < 1) {
                throw new StructuredModelFailure(
                        StructuredModelFailure.Code.DEADLINE_EXCEEDED);
            }
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofMillis(timeout))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
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
                throw new StructuredModelFailure(classifyHttpStatus(response.statusCode()));
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

    private String body(StructuredModelRequest request) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", provider.getModelName());
        payload.put("messages", List.of(
                Map.of("role", "system", "content", request.systemPrompt()),
                Map.of("role", "user", "content", request.userPrompt())));
        ModelProviderProtocolProfile.forProvider(provider.getProviderId())
                .applyStructuredOutputFields(payload);
        payload.put("max_tokens", request.maxOutputTokens());
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
