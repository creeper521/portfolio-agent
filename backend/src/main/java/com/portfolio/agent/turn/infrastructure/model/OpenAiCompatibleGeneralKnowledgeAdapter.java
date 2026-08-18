package com.portfolio.agent.turn.infrastructure.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.adapter.model.ModelProviderDescriptor;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeModelPort;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeRequest;
import com.portfolio.agent.turn.capability.general.GeneralKnowledgeUnavailableException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** HTTPS JSON-mode adapter dedicated to General knowledge generation. */
public final class OpenAiCompatibleGeneralKnowledgeAdapter implements GeneralKnowledgeModelPort {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI endpoint;
    private final String model;
    private final String apiKey;
    private final int maxTokens;
    private final Duration operationTimeout;

    public OpenAiCompatibleGeneralKnowledgeAdapter(
            HttpClient httpClient, ObjectMapper objectMapper,
            ModelProviderDescriptor descriptor, String apiKey,
            int maxTokens, Duration operationTimeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.endpoint = Objects.requireNonNull(descriptor, "descriptor").getEndpoint();
        this.model = descriptor.getModelName();
        this.apiKey = apiKey == null ? "" : apiKey;
        if (maxTokens < 1) throw new IllegalArgumentException("maxTokens must be positive");
        this.maxTokens = maxTokens;
        this.operationTimeout = Objects.requireNonNull(operationTimeout, "operationTimeout");
    }

    @Override public String generate(GeneralKnowledgeRequest request) {
        long remaining = request.getDeadline().remainingMillis();
        long timeoutMillis = Math.min(operationTimeout.toMillis(), remaining);
        if (timeoutMillis < 1) throw new GeneralKnowledgeUnavailableException("deadline expired");
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofMillis(timeoutMillis))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body(request)))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new GeneralKnowledgeUnavailableException("provider returned a non-success status");
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.size() != 1) {
                throw new GeneralKnowledgeUnavailableException("provider response shape is invalid");
            }
            JsonNode content = choices.get(0).path("message").get("content");
            if (content == null || !content.isTextual() || content.textValue().isBlank()) {
                throw new GeneralKnowledgeUnavailableException("provider response is empty");
            }
            return content.textValue();
        } catch (GeneralKnowledgeUnavailableException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new GeneralKnowledgeUnavailableException("provider call interrupted", exception);
        } catch (Exception exception) {
            throw new GeneralKnowledgeUnavailableException("provider call failed", exception);
        }
    }

    private String body(GeneralKnowledgeRequest request) throws Exception {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("kind", request.getKind());
        input.put("topic", request.getTopic());
        input.put("subjects", request.getSubjects());
        input.put("dimensions", request.getDimensions());
        input.put("depth", request.getDepth());
        input.put("audience", request.getAudience());
        input.put("expectedContentVersion", request.getExpectedContentVersion());
        String system = "Return only one JSON object. Root fields must be topic, statements, caveats. "
                + "Each statement has role, text and optional subject/dimension. "
                + "Explanation requires DEFINITION and MECHANISM. Comparison uses COMPARISON.";
        String user = objectMapper.writeValueAsString(input);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)));
        payload.put("response_format", Map.of("type", "json_object"));
        payload.put("thinking", Map.of("type", "disabled"));
        payload.put("stream", false);
        payload.put("max_tokens", maxTokens);
        payload.put("temperature", 0.2d);
        return objectMapper.writeValueAsString(payload);
    }
}
