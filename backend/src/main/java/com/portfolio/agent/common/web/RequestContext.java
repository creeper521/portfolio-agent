package com.portfolio.agent.common.web;

import java.util.UUID;

public final class RequestContext {

    private final String traceId;
    private final String requestId;
    private final String clientSessionId;
    private final String clientRequestId;
    private String turnId;

    private RequestContext(
            String traceId,
            String requestId,
            String clientSessionId,
            String clientRequestId,
            String turnId
    ) {
        this.traceId = traceId;
        this.requestId = requestId;
        this.clientSessionId = clientSessionId;
        this.clientRequestId = clientRequestId;
        this.turnId = turnId;
    }

    public static RequestContext create(String clientSessionId, String clientRequestId) {
        return new RequestContext(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                canonicalUuidOrNull(clientSessionId),
                canonicalUuidOrNull(clientRequestId),
                null);
    }

    public String getTraceId() {
        return traceId;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getClientSessionId() {
        return clientSessionId;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public String getTurnId() {
        return turnId;
    }

    public RequestContext copy() {
        return new RequestContext(traceId, requestId, clientSessionId, clientRequestId, turnId);
    }

    void setTurnId(String turnId) {
        this.turnId = canonicalUuidOrNull(turnId);
    }

    static String canonicalUuidOrNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            UUID parsed = UUID.fromString(value);
            if (parsed.toString().equals(value)) {
                return value;
            }
        } catch (IllegalArgumentException exception) {
            return null;
        }
        return null;
    }
}
