package com.portfolio.agent.common.web;

import java.time.OffsetDateTime;
import java.util.Objects;

public final class ApiErrorResponse {

    private final String requestId;
    private final String code;
    private final String message;
    private final OffsetDateTime timestamp;

    public ApiErrorResponse(
            String requestId,
            String code,
            String message,
            OffsetDateTime timestamp
    ) {
        this.requestId = requestId;
        this.code = code;
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ApiErrorResponse that)) {
            return false;
        }
        return Objects.equals(requestId, that.requestId)
                && Objects.equals(code, that.code)
                && Objects.equals(message, that.message)
                && Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestId, code, message, timestamp);
    }

    @Override
    public String toString() {
        return "ApiErrorResponse{" +
                "requestId='" + requestId + '\'' +
                ", code='" + code + '\'' +
                ", message='" + message + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
