package com.portfolio.agent.common.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestContextTest {

    private static final String CLIENT_SESSION_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TURN_ID = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.clear();
        MDC.clear();
    }

    @Test
    void acceptsOnlyCanonicalClientUuids() {
        RequestContext context = RequestContext.create(CLIENT_SESSION_ID, "not-a-uuid");

        assertThat(context.getClientSessionId()).isEqualTo(CLIENT_SESSION_ID);
        assertThat(context.getClientRequestId()).isNull();
    }

    @Test
    void rejectsNonCanonicalUuidFormsAndCopiesOnlyValidatedFields() {
        RequestContext context = RequestContext.create(
                CLIENT_SESSION_ID.toUpperCase(),
                "550e8400e29b41d4a716446655440000");

        context.setTurnId(TURN_ID);
        RequestContext copy = context.copy();

        assertThat(context.getClientSessionId()).isNull();
        assertThat(context.getClientRequestId()).isNull();
        assertThat(copy).isNotSameAs(context);
        assertThat(copy.getTurnId()).isEqualTo(TURN_ID);
    }

    @Test
    void callWithInstallsCopiedContextAndAlwaysCleansMdcAndThreadLocal() throws Exception {
        RequestContext context = RequestContext.create(CLIENT_SESSION_ID, null);
        context.setTurnId(TURN_ID);
        Callable<String> action = () -> {
            RequestContext current = RequestContextHolder.requireCurrent();
            assertThat(current).isNotSameAs(context);
            assertThat(current.getRequestId()).isEqualTo(context.getRequestId());
            assertThat(MDC.get("trace.id")).isEqualTo(context.getTraceId());
            return current.getTurnId();
        };

        String turnId = RequestContextHolder.callWith(context, action);
        Optional<RequestContext> current = RequestContextHolder.current();

        assertThat(turnId).isEqualTo(TURN_ID);
        assertThat(current).isEmpty();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void callWithRestoresPreviousContextAndCompleteMdc() throws Exception {
        RequestContext previous = RequestContext.create(CLIENT_SESSION_ID, null);
        RequestContextHolder.set(previous);
        MDC.put("custom.key", "preserved");
        MDC.put("client.session.id", "stale-client");
        MDC.put("turn.id", "stale-turn");
        MDC.put("http.method", "STALE");
        MDC.put("http.route", "/stale-route");
        Map<String, String> previousMdc = MDC.getCopyOfContextMap();
        RequestContext context = RequestContext.create(null, null);

        RequestContextHolder.callWith(context, () -> {
            assertThat(RequestContextHolder.requireCurrent().getRequestId())
                    .isEqualTo(context.getRequestId());
            assertThat(MDC.get("client.session.id")).isNull();
            assertThat(MDC.get("turn.id")).isNull();
            assertThat(MDC.get("http.method")).isNull();
            assertThat(MDC.get("http.route")).isNull();
            assertThat(MDC.get("custom.key")).isEqualTo("preserved");
            return null;
        });

        assertThat(RequestContextHolder.requireCurrent()).isSameAs(previous);
        assertThat(MDC.getCopyOfContextMap()).isEqualTo(previousMdc);
    }

    @Test
    void nestedCallWithRestoresTheOuterInstalledCopy() throws Exception {
        RequestContext outer = RequestContext.create(CLIENT_SESSION_ID, null);
        RequestContext inner = RequestContext.create(null, null);

        RequestContextHolder.callWith(outer, () -> {
            RequestContext installedOuter = RequestContextHolder.requireCurrent();
            RequestContextHolder.callWith(inner, () -> {
                assertThat(RequestContextHolder.requireCurrent().getRequestId())
                        .isEqualTo(inner.getRequestId());
                return null;
            });
            assertThat(RequestContextHolder.requireCurrent()).isSameAs(installedOuter);
            assertThat(MDC.get("request.id")).isEqualTo(outer.getRequestId());
            return null;
        });

        assertThat(RequestContextHolder.current()).isEmpty();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void callWithRestoresPreviousStateWhenActionThrows() {
        RequestContext previous = RequestContext.create(CLIENT_SESSION_ID, null);
        RequestContextHolder.set(previous);
        MDC.put("custom.key", "before");
        Map<String, String> previousMdc = MDC.getCopyOfContextMap();
        IllegalStateException failure = new IllegalStateException("expected");

        assertThatThrownBy(() -> RequestContextHolder.callWith(
                RequestContext.create(null, null),
                () -> {
                    throw failure;
                }))
                .isSameAs(failure);

        assertThat(RequestContextHolder.requireCurrent()).isSameAs(previous);
        assertThat(MDC.getCopyOfContextMap()).isEqualTo(previousMdc);
    }
}
