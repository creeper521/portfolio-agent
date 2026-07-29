package com.portfolio.agent.common.web;

import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.DiagnosticLevel;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

public final class RequestDiagnosticsFilter extends OncePerRequestFilter {

    public static final String FAILURE_ATTRIBUTE =
            RequestDiagnosticsFilter.class.getName() + ".failure";
    public static final String ERROR_CODE_ATTRIBUTE =
            RequestDiagnosticsFilter.class.getName() + ".errorCode";

    private static final String UNRESOLVED_ROUTE = "UNRESOLVED";
    private static final String UNMATCHED_ROUTE = "UNMATCHED";

    private final DiagnosticEventPublisher publisher;

    public RequestDiagnosticsFilter(DiagnosticEventPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        RequestContext context = null;
        Throwable chainFailure = null;
        long startedAt = System.nanoTime();
        try {
            context = RequestContext.create(
                    request.getHeader("X-Client-Session-Id"),
                    request.getHeader("X-Client-Request-Id"));
            RequestContextHolder.set(context);
            initializeDiagnosticsSafely(request, response, context);
            try {
                filterChain.doFilter(request, response);
            } catch (Throwable failure) {
                chainFailure = failure;
                throw failure;
            }
        } finally {
            try {
                finalizeDiagnosticsSafely(
                        request, response, startedAt, context, chainFailure);
            } finally {
                try {
                    MDC.clear();
                } finally {
                    RequestContextHolder.clear();
                }
            }
        }
    }

    private void initializeDiagnosticsSafely(
            HttpServletRequest request,
            HttpServletResponse response,
            RequestContext context
    ) {
        try {
            RequestContextHolder.putMdc(context);
            MDC.put("http.method", request.getMethod());
            MDC.put("http.route", UNRESOLVED_ROUTE);
            response.setHeader("X-Request-Id", context.getRequestId());
            response.setHeader("X-Trace-Id", context.getTraceId());
            publishStarted(request.getMethod(), context);
        } catch (Throwable diagnosticsFailure) {
            // Diagnostics are fail-safe and never replace request processing.
        }
    }

    private void finalizeDiagnosticsSafely(
            HttpServletRequest request,
            HttpServletResponse response,
            long startedAt,
            RequestContext context,
            Throwable chainFailure
    ) {
        if (context == null) {
            return;
        }
        try {
            String route = Optional.ofNullable(request.getAttribute(
                    HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE))
                    .map(Object::toString)
                    .orElse(UNMATCHED_ROUTE);
            MDC.put("http.route", route);
            publishCompletion(
                    request, response, startedAt, context, route, chainFailure);
        } catch (Throwable diagnosticsFailure) {
            // Final diagnostics are best effort; cleanup is owned by the outer finally.
        }
    }

    private void publishStarted(String method, RequestContext context) {
        DiagnosticEvent.Builder builder = eventBuilder(
                "http.request.started", DiagnosticLevel.INFO, method, UNRESOLVED_ROUTE, context);
        publish(builder.build());
    }

    private void publishCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            long startedAt,
            RequestContext context,
            String route,
            Throwable chainFailure
    ) {
        int status = response.getStatus();
        RequestFailure failure = (RequestFailure) request.getAttribute(FAILURE_ATTRIBUTE);
        String errorCode = (String) request.getAttribute(ERROR_CODE_ATTRIBUTE);
        long durationMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        if (errorCode != null) {
            publishRejected(request.getMethod(), route, status, durationMillis, context,
                    errorCode);
        } else if (chainFailure != null || failure != null || status >= 500) {
            publishFailure(request.getMethod(), route, status, durationMillis,
                    context, failure, chainFailure);
        } else if (status >= 400) {
            publishRejected(request.getMethod(), route, status, durationMillis, context,
                    null);
        } else {
            publishCompleted(request.getMethod(), route, status, durationMillis, context);
        }
    }

    private void publishCompleted(
            String method,
            String route,
            int status,
            long durationMillis,
            RequestContext context
    ) {
        DiagnosticEvent.Builder builder = eventBuilder(
                "http.request.completed", DiagnosticLevel.INFO, method, route, context)
                .field("http.status_code", status)
                .field("duration.ms", durationMillis);
        publish(builder.build());
    }

    private void publishRejected(
            String method,
            String route,
            int status,
            long durationMillis,
            RequestContext context,
            String errorCode
    ) {
        DiagnosticLevel level = status == 429
                || "ANSWER_REQUEST_TIMEOUT".equals(errorCode)
                ? DiagnosticLevel.WARN
                : DiagnosticLevel.INFO;
        DiagnosticEvent.Builder builder = eventBuilder(
                "http.request.rejected", level, method, route, context)
                .field("http.status_code", status)
                .field("duration.ms", durationMillis);
        if (errorCode != null) {
            builder.field("error.code", errorCode);
        }
        publish(builder.build());
    }

    private void publishFailure(
            String method,
            String route,
            int status,
            long durationMillis,
            RequestContext context,
            RequestFailure failure,
            Throwable chainFailure
    ) {
        DiagnosticEvent.Builder builder = eventBuilder(
                "http.request.failed", DiagnosticLevel.ERROR, method, route, context)
                .field("http.status_code", status)
                .field("duration.ms", durationMillis);
        if (failure != null) {
            builder.field("error.code", failure.getErrorCode())
                    .field("failure.exception_type", failure.getExceptionType())
                    .field("failure.frames", failure.getSafeRenderedFrames());
        } else if (chainFailure != null) {
            builder.field("failure.exception_type", chainFailure.getClass().getName());
        }
        publish(builder.build());
    }

    private DiagnosticEvent.Builder eventBuilder(
            String name,
            DiagnosticLevel level,
            String method,
            String route,
            RequestContext context
    ) {
        DiagnosticEvent.Builder builder = DiagnosticEvent.builder(name, level)
                .field("trace.id", context.getTraceId())
                .field("request.id", context.getRequestId())
                .field("http.method", method)
                .field("http.route", route);
        addOptionalField(builder, "client.session.id", context.getClientSessionId());
        addOptionalField(builder, "client.request.id", context.getClientRequestId());
        addOptionalField(builder, "turn.id", context.getTurnId());
        return builder;
    }

    private void addOptionalField(DiagnosticEvent.Builder builder, String key, String value) {
        if (value != null) {
            builder.field(key, value);
        }
    }

    private void publish(DiagnosticEvent event) {
        try {
            publisher.publish(event);
        } catch (Throwable diagnosticsFailure) {
            // Diagnostics must never change the request response or control flow.
        }
    }
}
