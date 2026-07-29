package com.portfolio.agent.common.web;

import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestDiagnosticsFilterTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.clear();
        MDC.clear();
    }

    @Test
    void publishesOnlySafeRouteMetadataAndCleansRequestState() throws Exception {
        List<DiagnosticEvent> events = new ArrayList<>();
        DiagnosticEventPublisher publisher = events::add;
        RequestDiagnosticsFilter filter = new RequestDiagnosticsFilter(publisher);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/private/path/not-for-logs");
        request.setQueryString("secret=value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
            servletRequest.setAttribute(
                    HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v2/answers");
            ((MockHttpServletResponse) servletResponse).setStatus(200);
        };

        filter.doFilter(request, response, chain);

        assertThat(events).hasSize(2);
        DiagnosticEvent started = events.get(0);
        DiagnosticEvent completed = events.get(1);
        Map<String, Object> completedFields = completed.getFields();
        assertThat(started.getName()).isEqualTo("http.request.started");
        assertThat(started.getFields()).containsEntry("http.route", "UNRESOLVED");
        assertThat(completed.getName()).isEqualTo("http.request.completed");
        assertThat(completedFields).containsEntry("http.route", "/api/v2/answers");
        assertThat(response.getHeader("X-Request-Id"))
                .isEqualTo(completedFields.get("request.id"));
        assertThat(response.getHeader("X-Trace-Id"))
                .isEqualTo(completedFields.get("trace.id"));
        assertThatCodeIsCanonicalUuid(response.getHeader("X-Request-Id"));
        assertThatCodeIsCanonicalUuid(response.getHeader("X-Trace-Id"));
        assertThat(events.toString()).doesNotContain("/private/path/not-for-logs", "secret=value");
        assertThat(RequestContextHolder.current()).isEmpty();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void publishesUnmatchedRouteWithoutReadingTheRequestUri() throws Exception {
        List<DiagnosticEvent> events = new ArrayList<>();
        RequestDiagnosticsFilter filter = new RequestDiagnosticsFilter(events::add);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/unknown/internal-path");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) ->
                ((MockHttpServletResponse) servletResponse).setStatus(404);

        filter.doFilter(request, response, chain);

        assertThat(events).hasSize(2);
        assertThat(events.get(1).getName()).isEqualTo("http.request.rejected");
        assertThat(events.get(1).getFields()).containsEntry("http.route", "UNMATCHED");
        assertThat(events.toString()).doesNotContain("/unknown/internal-path");
    }

    @Test
    void entryPublisherFailureDoesNotChangeTheResponseAndStillCleansState() {
        AtomicInteger invocations = new AtomicInteger();
        List<DiagnosticEvent> events = new ArrayList<>();
        RequestDiagnosticsFilter filter = new RequestDiagnosticsFilter(event -> {
            if (invocations.incrementAndGet() == 1) {
                throw new AssertionError("entry diagnostics failed");
            }
            events.add(event);
        });
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/private");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) ->
                ((MockHttpServletResponse) servletResponse).setStatus(204);

        assertThatCode(() -> filter.doFilter(request, response, chain)).doesNotThrowAnyException();

        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(events).extracting(DiagnosticEvent::getName)
                .containsExactly("http.request.completed");
        assertRequestStateIsClean();
    }

    @Test
    void finalPublisherFailureDoesNotChangeTheResponseAndStillCleansState() {
        AtomicInteger invocations = new AtomicInteger();
        List<DiagnosticEvent> events = new ArrayList<>();
        RequestDiagnosticsFilter filter = new RequestDiagnosticsFilter(event -> {
            if (invocations.incrementAndGet() == 2) {
                throw new AssertionError("final diagnostics failed");
            }
            events.add(event);
        });
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/private");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) ->
                ((MockHttpServletResponse) servletResponse).setStatus(202);

        assertThatCode(() -> filter.doFilter(request, response, chain)).doesNotThrowAnyException();

        assertThat(response.getStatus()).isEqualTo(202);
        assertThat(events).extracting(DiagnosticEvent::getName)
                .containsExactly("http.request.started");
        assertRequestStateIsClean();
    }

    @Test
    void chainFailurePublishesExactlyOneFailedEventAndRethrowsTheOriginal() {
        List<DiagnosticEvent> events = new ArrayList<>();
        RequestDiagnosticsFilter filter = new RequestDiagnosticsFilter(events::add);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/private");
        MockHttpServletResponse response = new MockHttpServletResponse();
        IllegalStateException failure = new IllegalStateException("business failure");
        FilterChain chain = (servletRequest, servletResponse) -> {
            throw failure;
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, chain)).isSameAs(failure);

        assertThat(events).extracting(DiagnosticEvent::getName)
                .containsExactly("http.request.started", "http.request.failed");
        assertThat(events.get(1).getFields())
                .containsEntry("failure.exception_type", IllegalStateException.class.getName());
        assertRequestStateIsClean();
    }

    @Test
    void failedEventPublisherFailureDoesNotReplaceChainFailureAndStillCleansState() {
        IllegalStateException businessFailure = new IllegalStateException("business failure");
        RuntimeException publisherFailure = new RuntimeException("publisher failure");
        AtomicInteger failedEventInvocations = new AtomicInteger();
        RequestDiagnosticsFilter filter = new RequestDiagnosticsFilter(event -> {
            if ("http.request.failed".equals(event.getName())) {
                failedEventInvocations.incrementAndGet();
                throw publisherFailure;
            }
        });
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/private");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> {
            throw businessFailure;
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isSameAs(businessFailure);

        assertThat(failedEventInvocations).hasValue(1);
        assertRequestStateIsClean();
    }

    @Test
    void serverErrorStatusPublishesFailed() throws Exception {
        List<DiagnosticEvent> events = new ArrayList<>();
        RequestDiagnosticsFilter filter = new RequestDiagnosticsFilter(events::add);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/private");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                ((MockHttpServletResponse) servletResponse).setStatus(500));

        assertThat(events).extracting(DiagnosticEvent::getName)
                .containsExactly("http.request.started", "http.request.failed");
        assertThat(events.get(1).getFields()).containsEntry("http.status_code", 500);
    }

    @Test
    void requestFailureMetadataIsPublishedByTheSingleFailedEvent() throws Exception {
        List<DiagnosticEvent> events = new ArrayList<>();
        RequestDiagnosticsFilter filter = new RequestDiagnosticsFilter(events::add);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/private");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestFailure failure = new RequestFailure(
                "INTERNAL_ERROR", IllegalStateException.class.getName(), "safe-frame");

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                servletRequest.setAttribute(RequestDiagnosticsFilter.FAILURE_ATTRIBUTE, failure));

        assertThat(events).extracting(DiagnosticEvent::getName)
                .containsExactly("http.request.started", "http.request.failed");
        assertThat(events.get(1).getFields())
                .containsEntry("error.code", "INTERNAL_ERROR")
                .containsEntry("failure.exception_type", IllegalStateException.class.getName())
                .containsEntry("failure.frames", "safe-frame");
    }

    @Test
    void rejectedResponsePublishesItsStableErrorCode() throws Exception {
        List<DiagnosticEvent> events = new ArrayList<>();
        RequestDiagnosticsFilter filter = new RequestDiagnosticsFilter(events::add);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/private");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            servletRequest.setAttribute(RequestDiagnosticsFilter.ERROR_CODE_ATTRIBUTE,
                    "VALIDATION_ERROR");
            ((MockHttpServletResponse) servletResponse).setStatus(400);
        });

        assertThat(events).extracting(DiagnosticEvent::getName)
                .containsExactly("http.request.started", "http.request.rejected");
        assertThat(events.get(1).getFields())
                .containsEntry("error.code", "VALIDATION_ERROR");
    }

    @Test
    void explicitExpectedCodeMakesServerErrorRejectedInsteadOfFailed() throws Exception {
        List<DiagnosticEvent> events = new ArrayList<>();
        RequestDiagnosticsFilter filter = new RequestDiagnosticsFilter(events::add);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/private");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            servletRequest.setAttribute(
                    RequestDiagnosticsFilter.ERROR_CODE_ATTRIBUTE,
                    "ANSWER_REQUEST_TIMEOUT");
            servletRequest.setAttribute(
                    RequestDiagnosticsFilter.FAILURE_ATTRIBUTE,
                    new RequestFailure(
                            "INTERNAL_ERROR",
                            IllegalStateException.class.getName(),
                            "safe-frame"));
            ((MockHttpServletResponse) servletResponse).setStatus(503);
        });

        assertThat(events).extracting(DiagnosticEvent::getName)
                .containsExactly("http.request.started", "http.request.rejected");
        assertThat(events.get(1).getFields())
                .containsEntry("http.status_code", 503)
                .containsEntry("error.code", "ANSWER_REQUEST_TIMEOUT")
                .doesNotContainKeys("failure.exception_type", "failure.frames");
    }

    @Test
    void configurationRegistersTheFilterAtTheRequiredOrder() {
        RequestDiagnosticsConfiguration configuration = new RequestDiagnosticsConfiguration();
        FilterRegistrationBean<RequestDiagnosticsFilter> registration =
                configuration.requestDiagnosticsFilter(event -> { });

        Order bodyLimitOrder = FrontendDiagnosticsBodyLimitFilter.class
                .getAnnotation(Order.class);

        assertThat(bodyLimitOrder).isNotNull();
        assertThat(registration.getOrder()).isLessThan(bodyLimitOrder.value());
    }

    @Test
    void expectedTimeoutIsWarningWhileOrdinaryRejectionsRemainInfo() throws Exception {
        List<DiagnosticEvent> events = new ArrayList<>();
        RequestDiagnosticsFilter filter = new RequestDiagnosticsFilter(events::add);

        MockHttpServletRequest timeoutRequest =
                new MockHttpServletRequest("POST", "/api/v2/answers");
        MockHttpServletResponse timeoutResponse = new MockHttpServletResponse();
        filter.doFilter(timeoutRequest, timeoutResponse, (request, response) -> {
            request.setAttribute(
                    RequestDiagnosticsFilter.ERROR_CODE_ATTRIBUTE,
                    "ANSWER_REQUEST_TIMEOUT");
            ((MockHttpServletResponse) response).setStatus(503);
        });

        MockHttpServletRequest notFoundRequest =
                new MockHttpServletRequest("GET", "/missing");
        MockHttpServletResponse notFoundResponse = new MockHttpServletResponse();
        filter.doFilter(notFoundRequest, notFoundResponse, (request, response) -> {
            request.setAttribute(
                    RequestDiagnosticsFilter.ERROR_CODE_ATTRIBUTE,
                    "NOT_FOUND");
            ((MockHttpServletResponse) response).setStatus(404);
        });

        assertThat(events).filteredOn(event ->
                        "http.request.rejected".equals(event.getName()))
                .extracting(DiagnosticEvent::getLevel)
                .containsExactly(
                        com.portfolio.agent.common.observability.DiagnosticLevel.WARN,
                        com.portfolio.agent.common.observability.DiagnosticLevel.INFO);
    }

    private void assertThatCodeIsCanonicalUuid(String value) {
        UUID parsed = UUID.fromString(value);
        assertThat(parsed.toString()).isEqualTo(value);
    }

    private void assertRequestStateIsClean() {
        assertThat(RequestContextHolder.current()).isEmpty();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
