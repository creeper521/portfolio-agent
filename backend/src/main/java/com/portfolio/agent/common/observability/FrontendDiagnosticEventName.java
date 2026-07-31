package com.portfolio.agent.common.observability;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum FrontendDiagnosticEventName {

    APPLICATION_STARTED("frontend.application.started", DiagnosticLevel.INFO),
    CONTENT_LOAD_COMPLETED("frontend.content.load.completed", DiagnosticLevel.INFO),
    AGENT_REQUEST_COMPLETED("frontend.agent.request.completed", DiagnosticLevel.INFO),
    CONTENT_LOAD_FAILED("frontend.content.load.failed", DiagnosticLevel.ERROR),
    AGENT_REQUEST_FAILED("frontend.agent.request.failed", DiagnosticLevel.ERROR),
    AGENT_REQUEST_SLOW("frontend.agent.request.slow", DiagnosticLevel.WARN),
    AGENT_REQUEST_CANCELLED("frontend.agent.request.cancelled", DiagnosticLevel.INFO),
    RESPONSE_INVALID("frontend.response.invalid", DiagnosticLevel.WARN),
    RUNTIME_FAILED("frontend.runtime.failed", DiagnosticLevel.ERROR);

    private final String value;
    private final DiagnosticLevel level;

    FrontendDiagnosticEventName(String value, DiagnosticLevel level) {
        this.value = value;
        this.level = level;
    }

    @JsonCreator
    public static FrontendDiagnosticEventName fromValue(String value) {
        for (FrontendDiagnosticEventName eventName : values()) {
            if (eventName.value.equals(value)) {
                return eventName;
            }
        }
        throw new IllegalArgumentException("unknown frontend diagnostic event name");
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public DiagnosticLevel getLevel() {
        return level;
    }
}
