package com.portfolio.agent.infrastructure.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputContractRegistry;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

/** Test-only bridge for integration tests outside the transport package. */
public final class LoopbackStructuredTransportTestSupport {
    private LoopbackStructuredTransportTestSupport() { }

    public static OpenAiCompatibleStructuredModelTransport transport(
            URI endpoint, ObjectMapper mapper,
            StructuredOutputContractRegistry contracts) {
        return new OpenAiCompatibleStructuredModelTransport(
                HttpClient.newHttpClient(), mapper, Duration.ofSeconds(2),
                event -> { }, contracts, ignored -> endpoint);
    }
}
