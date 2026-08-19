package com.portfolio.agent.infrastructure.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.adapter.model.ModelProviderDescriptor;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.DiagnosticLevel;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OpenAiCompatibleStructuredModelTransport implements StructuredModelTransport {
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final ModelProviderDescriptor provider;
    private final String apiKey;
    private final Duration operationTimeout;
    private final DiagnosticEventPublisher diagnostics;

    public OpenAiCompatibleStructuredModelTransport(
            HttpClient client, ObjectMapper mapper, ModelProviderDescriptor provider,
            String apiKey, Duration operationTimeout, DiagnosticEventPublisher diagnostics) {
        this.client = client; this.mapper = mapper; this.provider = provider;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.operationTimeout = operationTimeout;
        this.diagnostics = diagnostics;
    }

    @Override public StructuredModelResponse execute(StructuredModelRequest request) {
        long startedAt = System.nanoTime();
        long timeout = Math.min(operationTimeout.toMillis(), request.deadline().remainingMillis());
        if (timeout < 1) throw new StructuredModelFailure(StructuredModelFailure.Code.DEADLINE_EXCEEDED);
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(provider.getEndpoint())
                    .timeout(Duration.ofMillis(timeout))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body(request))).build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new StructuredModelFailure(StructuredModelFailure.Code.PROVIDER_REJECTED);
            }
            JsonNode root = mapper.readTree(response.body());
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.size() != 1) {
                throw new StructuredModelFailure(StructuredModelFailure.Code.INVALID_RESPONSE);
            }
            JsonNode content = choices.get(0).path("message").get("content");
            if (content == null || !content.isTextual() || content.textValue().isBlank()) {
                throw new StructuredModelFailure(StructuredModelFailure.Code.INVALID_RESPONSE);
            }
            StructuredModelResponse result = new StructuredModelResponse(content.textValue());
            publish(request.operation(), true, null, startedAt);
            return result;
        } catch (StructuredModelFailure failure) {
            publish(request.operation(), false, failure.getCode().name(), startedAt);
            throw failure;
        }
        catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            publish(request.operation(), false,
                    StructuredModelFailure.Code.TRANSPORT_UNAVAILABLE.name(), startedAt);
            throw new StructuredModelFailure(StructuredModelFailure.Code.TRANSPORT_UNAVAILABLE, failure);
        } catch (Exception failure) {
            publish(request.operation(), false,
                    StructuredModelFailure.Code.TRANSPORT_UNAVAILABLE.name(), startedAt);
            throw new StructuredModelFailure(StructuredModelFailure.Code.TRANSPORT_UNAVAILABLE, failure);
        }
    }

    private void publish(String operation, boolean success, String failureCode, long startedAt) {
        try {
            DiagnosticEvent.Builder event = DiagnosticEvent.builder(
                            success ? "provider.call.completed" : "provider.call.failed",
                            success ? DiagnosticLevel.DEBUG : DiagnosticLevel.WARN)
                    .field("provider.operation", operation)
                    .field("event.outcome", success ? "SUCCESS" : "FAILURE")
                    .field("duration.bucket", durationBucket(startedAt))
                    .field("response.present", success);
            if (failureCode != null) event.field("failure.code", failureCode);
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
        payload.put("response_format", Map.of("type", "json_object"));
        payload.put("thinking", Map.of("type", "disabled"));
        payload.put("stream", false);
        payload.put("max_tokens", request.maxOutputTokens());
        payload.put("temperature", request.temperature());
        return mapper.writeValueAsString(payload);
    }
}
