package com.portfolio.agent.answer.composition.adapter.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.composition.gateway.PortfolioExpressionProviderException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/** One-shot HTTPS transport. Retry and provider switching are intentionally absent. */
public final class JdkPortfolioExpressionTransport implements PortfolioExpressionTransport {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public JdkPortfolioExpressionTransport(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public String post(String endpoint, String apiKey, Object requestBody, Duration timeout) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + requireText(apiKey, "apiKey"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new PortfolioExpressionProviderException("expression provider rejected request");
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PortfolioExpressionProviderException("expression provider call interrupted", exception);
        } catch (PortfolioExpressionProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PortfolioExpressionProviderException("expression provider call failed", exception);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new PortfolioExpressionProviderException(field + " is unavailable");
        }
        return value.strip();
    }
}
