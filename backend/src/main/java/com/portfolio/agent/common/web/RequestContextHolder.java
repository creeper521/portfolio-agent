package com.portfolio.agent.common.web;

import org.slf4j.MDC;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

public final class RequestContextHolder {

    private static final ThreadLocal<RequestContext> CONTEXT = new ThreadLocal<>();

    private RequestContextHolder() {
    }

    public static Optional<RequestContext> current() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static RequestContext requireCurrent() {
        return current().orElseThrow(() -> new IllegalStateException("request context is not available"));
    }

    public static void set(RequestContext context) {
        CONTEXT.set(Objects.requireNonNull(context, "request context must not be null"));
    }

    public static void enrichTurnId(String turnId) {
        RequestContext context = requireCurrent();
        context.setTurnId(turnId);
        if (context.getTurnId() == null) {
            MDC.remove("turn.id");
        } else {
            MDC.put("turn.id", context.getTurnId());
        }
    }

    public static <T> T callWith(RequestContext context, Callable<T> action) throws Exception {
        RequestContext copiedContext = Objects.requireNonNull(
                context, "request context must not be null").copy();
        Callable<T> requiredAction = Objects.requireNonNull(action, "action must not be null");
        RequestContext previousContext = CONTEXT.get();
        Map<String, String> previousMdc = MDC.getCopyOfContextMap();
        try {
            set(copiedContext);
            clearManagedMdc();
            putMdc(copiedContext);
            return requiredAction.call();
        } finally {
            try {
                MDC.clear();
                if (previousMdc != null) {
                    MDC.setContextMap(previousMdc);
                }
            } finally {
                if (previousContext == null) {
                    clear();
                } else {
                    set(previousContext);
                }
            }
        }
    }

    public static void clear() {
        CONTEXT.remove();
    }

    static void putMdc(RequestContext context) {
        MDC.put("trace.id", context.getTraceId());
        MDC.put("request.id", context.getRequestId());
        putIfPresent("client.session.id", context.getClientSessionId());
        putIfPresent("client.request.id", context.getClientRequestId());
        putIfPresent("turn.id", context.getTurnId());
    }

    private static void clearManagedMdc() {
        MDC.remove("trace.id");
        MDC.remove("request.id");
        MDC.remove("client.session.id");
        MDC.remove("client.request.id");
        MDC.remove("turn.id");
        MDC.remove("http.method");
        MDC.remove("http.route");
    }

    private static void putIfPresent(String key, String value) {
        if (value != null) {
            MDC.put(key, value);
        }
    }
}
