package com.portfolio.agent.answer.composition.adapter.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.composition.domain.ModelExpressionDeadline;
import com.portfolio.agent.answer.composition.domain.ModelExpressionRequest;
import com.portfolio.agent.answer.composition.domain.ModelExpressionResult;
import com.portfolio.agent.answer.composition.gateway.PortfolioExpressionPort;
import com.portfolio.agent.answer.composition.gateway.PortfolioExpressionProviderException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Independent OpenAI-compatible expression adapter. It performs no retry or fallback call. */
public final class OpenAiCompatiblePortfolioExpressionAdapter implements PortfolioExpressionPort {
    private final PortfolioExpressionTransport transport;
    private final PortfolioExpressionPromptFactory promptFactory;
    private final ObjectMapper objectMapper;
    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final int maxOutputTokens;
    private final Duration configuredTimeout;
    private final Clock clock;
    private final PortfolioExpressionDiagnostics diagnostics;

    public OpenAiCompatiblePortfolioExpressionAdapter(
            PortfolioExpressionTransport transport,
            PortfolioExpressionPromptFactory promptFactory,
            ObjectMapper objectMapper,
            String endpoint,
            String apiKey,
            String model,
            int maxOutputTokens,
            Duration configuredTimeout,
            Clock clock,
            PortfolioExpressionDiagnostics diagnostics) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.promptFactory = Objects.requireNonNull(promptFactory, "promptFactory");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.endpoint = requireText(endpoint, "endpoint");
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = requireText(model, "model");
        if (maxOutputTokens < 1 || maxOutputTokens > 1600) {
            throw new IllegalArgumentException("max output tokens outside policy");
        }
        this.maxOutputTokens = maxOutputTokens;
        this.configuredTimeout = Objects.requireNonNull(configuredTimeout, "configuredTimeout");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        if (configuredTimeout.isNegative() || configuredTimeout.isZero()
                || configuredTimeout.compareTo(Duration.ofSeconds(4)) > 0) {
            throw new IllegalArgumentException("timeout outside policy");
        }
    }

    @Override
    public ModelExpressionResult express(ModelExpressionRequest request, ModelExpressionDeadline deadline) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(deadline, "deadline");
        Instant now = Instant.now(clock);
        Duration remaining = deadline.remaining(now);
        if (remaining.isNegative() || remaining.isZero()) {
            return ModelExpressionResult.empty();
        }
        Duration timeout = remaining.compareTo(configuredTimeout) < 0 ? remaining : configuredTimeout;
        long startedAt = System.nanoTime();
        boolean responsePresent = false;
        try {
            String raw = transport.post(endpoint, apiKey,
                    promptFactory.requestBody(request, model, maxOutputTokens), timeout);
            if (raw == null || raw.isBlank()) {
                diagnostics.failed(PortfolioExpressionDiagnostics.FailureCode.EMPTY_RESPONSE,
                        false, startedAt);
                return ModelExpressionResult.empty();
            }
            responsePresent = true;
            JsonNode root = objectMapper.readTree(raw);
            JsonNode content = root == null ? null : root.path("choices").path(0).path("message").path("content");
            if (content == null || content.isMissingNode() || !content.isTextual()
                    || content.asText().isBlank()) {
                diagnostics.failed(PortfolioExpressionDiagnostics.FailureCode.EMPTY_RESPONSE,
                        false, startedAt);
                return ModelExpressionResult.empty();
            }
            diagnostics.completed(startedAt);
            return ModelExpressionResult.success(content.asText());
        } catch (Exception exception) {
            diagnostics.failed(PortfolioExpressionDiagnostics.FailureCode.PROVIDER_ERROR,
                    responsePresent, startedAt);
            if (exception instanceof PortfolioExpressionProviderException providerException) {
                throw providerException;
            }
            throw new PortfolioExpressionProviderException("expression provider response invalid", exception);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
