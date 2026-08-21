package com.portfolio.agent.common.web;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.common.observability.AnonymousSourceHasher;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.FrontendDiagnosticAdmissionGate;
import com.portfolio.agent.common.observability.FrontendDiagnosticProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FrontendDiagnosticsControllerTest {

    private static final String CLIENT_SESSION_ID = "7d24aa68-a177-4d40-a4bc-6d2093362836";
    private static final String CLIENT_REQUEST_ID = "9fa855a9-8e4a-43ef-b658-dc783086ad17";
    private static final String SERVER_REQUEST_ID = "fed92927-ad7d-41f3-8d0e-55e18e7dfe72";
    private static final String TURN_ID = "246f488d-f907-4a1a-a6d7-71ed33911b99";

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void returnsAcceptedAndPublishesOneClosedSafeDiagnosticEvent() throws Exception {
        List<DiagnosticEvent> events = new ArrayList<>();
        MockMvc mvc = mvc(true, 30, events);
        MDC.put("request.id", "current-ingest-request-id");

        mvc.perform(post("/api/client-diagnostics")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.9");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBatch()))
                .andExpect(status().isAccepted());

        assertThat(events).hasSize(1);
        DiagnosticEvent event = events.get(0);
        assertThat(event.getName()).isEqualTo("frontend.agent.request.failed");
        assertThat(event.getLevel().name()).isEqualTo("ERROR");
        assertThat(event.getFields())
                .containsEntry("event.origin", "browser")
                .containsEntry("client.session.id", CLIENT_SESSION_ID)
                .containsEntry("client.request.id", CLIENT_REQUEST_ID)
                .containsEntry("client.reported_request_id", SERVER_REQUEST_ID)
                .containsEntry("turn.id", TURN_ID)
                .containsEntry("error.code", "ANSWER_TIMEOUT")
                .containsEntry("error.kind", "TIMEOUT")
                .containsEntry("error.fingerprint",
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
                .containsEntry("duration.bucket", "GE_5000_MS")
                .containsOnlyKeys(
                        "event.origin",
                        "client.session.id",
                        "client.request.id",
                        "client.reported_request_id",
                        "turn.id",
                        "error.code",
                        "error.kind",
                        "error.fingerprint",
                        "duration.bucket");
        String sourceHash = new AnonymousSourceHasher(new byte[32]).hash("198.51.100.9");
        assertThat(event.toString()).doesNotContain("198.51.100.9", sourceHash);
        assertThat(MDC.get("request.id")).isEqualTo("current-ingest-request-id");
    }

    @Test
    void reportsCancellationAtInfo() throws Exception {
        List<DiagnosticEvent> events = new ArrayList<>();
        MockMvc mvc = mvc(true, 30, events);

        mvc.perform(post("/api/client-diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBatch().replace(
                                "frontend.agent.request.failed",
                                "frontend.agent.request.cancelled")))
                .andExpect(status().isAccepted());

        assertThat(events).singleElement().satisfies(event ->
                assertThat(event.getLevel().name()).isEqualTo("INFO"));
    }

    @Test
    void returnsNotFoundWhenFrontendIngestIsDisabled() throws Exception {
        List<DiagnosticEvent> events = new ArrayList<>();
        MockMvc mvc = mvcWithBodyFilter(false, 30, events::add);

        mvc.perform(post("/api/client-diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBatch()))
                .andExpect(status().isNotFound());

        assertThat(events).isEmpty();
    }

    @Test
    void disabledIngestReturnsNotFoundBeforeMalformedJsonIsParsed() throws Exception {
        MockMvc mvc = mvcWithBodyFilter(false, 30, event -> { });

        mvc.perform(post("/api/client-diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isNotFound());
    }

    @Test
    void disabledIngestReturnsNotFoundBeforeUnknownFieldsAreParsed() throws Exception {
        MockMvc mvc = mvcWithBodyFilter(false, 30, event -> { });
        String content = validBatch().replace(
                "]}",
                "],\"unexpected\":\"closed-contract\"}");

        mvc.perform(post("/api/client-diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isNotFound());
    }

    @Test
    void disabledIngestReturnsNotFoundBeforeBodySizeIsChecked() throws Exception {
        MockMvc mvc = mvcWithBodyFilter(false, 30, event -> { });

        mvc.perform(post("/api/client-diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(" ".repeat(16_385)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsElevenEvents() throws Exception {
        MockMvc mvc = mvc(true, 30, new ArrayList<>());
        StringBuilder content = new StringBuilder("{\"events\":[");
        for (int index = 0; index < 11; index++) {
            if (index > 0) {
                content.append(',');
            }
            content.append(validEvent());
        }
        content.append("]}");

        mvc.perform(post("/api/client-diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownBatchField() throws Exception {
        assertBadRequest(validBatch().replace(
                "]}",
                "],\"unexpected\":\"closed-contract\"}"));
    }

    @Test
    void rejectsUnknownEventName() throws Exception {
        assertBadRequest(validBatch().replace(
                "frontend.agent.request.failed",
                "frontend.not-approved"));
    }

    @Test
    void rejectsInvalidUuid() throws Exception {
        assertBadRequest(validBatch().replace(CLIENT_REQUEST_ID, "not-a-uuid"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"message", "stack", "url"})
    void rejectsUnsafeEventFields(String unsafeField) throws Exception {
        assertBadRequest(validBatch().replace(
                "\"durationBucket\":\"GE_5000_MS\"",
                "\"durationBucket\":\"GE_5000_MS\",\""
                        + unsafeField + "\":\"must-not-enter\""));
    }

    @Test
    void returnsTooManyRequestsAfterThePerSourceEventBudgetIsConsumed() throws Exception {
        MockMvc mvc = mvc(true, 1, new ArrayList<>());

        mvc.perform(post("/api/client-diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBatch()))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/client-diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBatch()))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void rateLimitsResolvedClientSourcesIndependentlyBehindATrustedProxy() throws Exception {
        List<DiagnosticEvent> events = new ArrayList<>();
        FrontendDiagnosticProperties properties = properties(true, 1);
        FrontendDiagnosticsController controller = new FrontendDiagnosticsController(
                properties,
                gate(1),
                new ClientAddressResolver(true, Set.of("192.0.2.10")),
                new AnonymousSourceHasher(new byte[32]),
                events::add);
        MockMvc mvc = mvc(controller);

        mvc.perform(post("/api/client-diagnostics")
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.10");
                            request.addHeader("X-Forwarded-For", "198.51.100.11");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBatch()))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/client-diagnostics")
                        .with(request -> {
                            request.setRemoteAddr("192.0.2.10");
                            request.addHeader("X-Forwarded-For", "198.51.100.12");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBatch()))
                .andExpect(status().isAccepted());

        assertThat(events).hasSize(2);
    }

    @Test
    void publisherRuntimeExceptionDoesNotChangeAcceptedResponse() throws Exception {
        MockMvc mvc = mvcWithBodyFilter(true, 30, event -> {
            throw new IllegalStateException("publisher unavailable");
        });

        mvc.perform(post("/api/client-diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBatch()))
                .andExpect(status().isAccepted());
    }

    @Test
    void publisherErrorsAreNotSwallowed() throws Exception {
        AssertionError publisherFailure = new AssertionError("publisher invariant broken");
        MockMvc mvc = mvcWithBodyFilter(true, 30, event -> {
            throw publisherFailure;
        });

        mvc.perform(post("/api/client-diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBatch()))
                .andExpect(status().is5xxServerError());
    }

    private void assertBadRequest(String content) throws Exception {
        MockMvc mvc = mvc(true, 30, new ArrayList<>());
        mvc.perform(post("/api/client-diagnostics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isBadRequest());
    }

    private MockMvc mvc(
            boolean enabled,
            int eventsPerMinute,
            List<DiagnosticEvent> events
    ) {
        FrontendDiagnosticProperties properties = properties(enabled, eventsPerMinute);
        FrontendDiagnosticsController controller = new FrontendDiagnosticsController(
                properties,
                gate(eventsPerMinute),
                new ClientAddressResolver(false, Set.of()),
                new AnonymousSourceHasher(new byte[32]),
                events::add);
        return mvc(controller);
    }

    private MockMvc mvcWithBodyFilter(
            boolean enabled,
            int eventsPerMinute,
            DiagnosticEventPublisher publisher
    ) {
        FrontendDiagnosticProperties properties = properties(enabled, eventsPerMinute);
        FrontendDiagnosticsController controller = new FrontendDiagnosticsController(
                properties,
                gate(eventsPerMinute),
                new ClientAddressResolver(false, Set.of()),
                new AnonymousSourceHasher(new byte[32]),
                publisher);
        return mvc(controller, new FrontendDiagnosticsBodyLimitFilter(properties));
    }

    private FrontendDiagnosticProperties properties(
            boolean enabled,
            int eventsPerMinute
    ) {
        FrontendDiagnosticProperties properties = new FrontendDiagnosticProperties();
        properties.setFrontendIngestEnabled(enabled);
        properties.setFrontendEventsPerMinute(eventsPerMinute);
        return properties;
    }

    private FrontendDiagnosticAdmissionGate gate(int eventsPerMinute) {
        return new FrontendDiagnosticAdmissionGate(
                Clock.fixed(Instant.parse("2026-07-29T00:00:00Z"), ZoneOffset.UTC),
                eventsPerMinute);
    }

    private MockMvc mvc(FrontendDiagnosticsController controller) {
        return mvc(controller, null);
    }

    private MockMvc mvc(
            FrontendDiagnosticsController controller,
            FrontendDiagnosticsBodyLimitFilter filter
    ) {
        ObjectMapper objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder builder =
                MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper));
        if (filter != null) {
            builder.addFilters(filter);
        }
        return builder.build();
    }

    private String validBatch() {
        return "{\"events\":[" + validEvent() + "]}";
    }

    private String validEvent() {
        return """
                {
                  "schemaVersion":1,
                  "eventName":"frontend.agent.request.failed",
                  "occurredAt":"2026-07-29T00:00:00.000Z",
                  "clientSessionId":"%s",
                  "clientRequestId":"%s",
                  "serverRequestId":"%s",
                  "turnId":"%s",
                  "errorCode":"ANSWER_TIMEOUT",
                  "errorKind":"TIMEOUT",
                  "errorFingerprint":"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                  "durationBucket":"GE_5000_MS"
                }
                """.formatted(CLIENT_SESSION_ID, CLIENT_REQUEST_ID, SERVER_REQUEST_ID, TURN_ID);
    }
}
