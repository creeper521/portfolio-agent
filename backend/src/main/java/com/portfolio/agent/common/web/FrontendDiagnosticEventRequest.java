package com.portfolio.agent.common.web;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.portfolio.agent.common.observability.FrontendDiagnosticEventName;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class FrontendDiagnosticEventRequest {

    private static final String UUID_V4_PATTERN =
            "(?i)^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";
    private static final String INSTANT_PATTERN =
            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z$";
    private static final String ERROR_CODE_PATTERN = "^[A-Z][A-Z0-9_]{0,63}$";
    private static final String FINGERPRINT_PATTERN = "^[0-9a-f]{64}$";
    private static final String CONTENT_VERSION_PATTERN = "^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$";

    @NotNull
    @Min(1)
    @Max(1)
    private final Integer schemaVersion;

    @NotNull
    private final FrontendDiagnosticEventName eventName;

    @NotBlank
    @Size(min = 24, max = 24)
    @Pattern(regexp = INSTANT_PATTERN)
    private final String occurredAt;

    @NotBlank
    @Size(min = 36, max = 36)
    @Pattern(regexp = UUID_V4_PATTERN)
    private final String clientSessionId;

    @NotBlank
    @Size(min = 36, max = 36)
    @Pattern(regexp = UUID_V4_PATTERN)
    private final String clientRequestId;

    @Size(min = 36, max = 36)
    @Pattern(regexp = UUID_V4_PATTERN)
    private final String serverRequestId;

    @Size(min = 36, max = 36)
    @Pattern(regexp = UUID_V4_PATTERN)
    private final String turnId;

    @Size(min = 1, max = 64)
    @Pattern(regexp = ERROR_CODE_PATTERN)
    private final String errorCode;

    private final ErrorKind errorKind;

    @Size(min = 64, max = 64)
    @Pattern(regexp = FINGERPRINT_PATTERN)
    private final String errorFingerprint;

    private final DurationBucket durationBucket;

    @Min(100)
    @Max(599)
    private final Integer httpStatus;

    private final GenerationMode generationMode;
    private final Boolean degraded;
    private final GuidanceStage guidanceStage;

    @Min(0)
    @Max(3)
    private final Integer suggestedQuestionCount;

    @Size(max = 64)
    @Pattern(regexp = CONTENT_VERSION_PATTERN)
    private final String contentVersion;

    @Min(0)
    @Max(3)
    private final Integer recoveredCount;

    @JsonCreator
    public FrontendDiagnosticEventRequest(
            @JsonProperty("schemaVersion") Integer schemaVersion,
            @JsonProperty("eventName") FrontendDiagnosticEventName eventName,
            @JsonProperty("occurredAt") String occurredAt,
            @JsonProperty("clientSessionId") String clientSessionId,
            @JsonProperty("clientRequestId") String clientRequestId,
            @JsonProperty("serverRequestId") String serverRequestId,
            @JsonProperty("turnId") String turnId,
            @JsonProperty("errorCode") String errorCode,
            @JsonProperty("errorKind") ErrorKind errorKind,
            @JsonProperty("errorFingerprint") String errorFingerprint,
            @JsonProperty("durationBucket") DurationBucket durationBucket,
            @JsonProperty("httpStatus") Integer httpStatus,
            @JsonProperty("generationMode") GenerationMode generationMode,
            @JsonProperty("degraded") Boolean degraded,
            @JsonProperty("guidanceStage") GuidanceStage guidanceStage,
            @JsonProperty("suggestedQuestionCount") Integer suggestedQuestionCount,
            @JsonProperty("contentVersion") String contentVersion,
            @JsonProperty("recoveredCount") Integer recoveredCount
    ) {
        this.schemaVersion = schemaVersion;
        this.eventName = eventName;
        this.occurredAt = occurredAt;
        this.clientSessionId = clientSessionId;
        this.clientRequestId = clientRequestId;
        this.serverRequestId = serverRequestId;
        this.turnId = turnId;
        this.errorCode = errorCode;
        this.errorKind = errorKind;
        this.errorFingerprint = errorFingerprint;
        this.durationBucket = durationBucket;
        this.httpStatus = httpStatus;
        this.generationMode = generationMode;
        this.degraded = degraded;
        this.guidanceStage = guidanceStage;
        this.suggestedQuestionCount = suggestedQuestionCount;
        this.contentVersion = contentVersion;
        this.recoveredCount = recoveredCount;
    }

    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    public FrontendDiagnosticEventName getEventName() {
        return eventName;
    }

    public String getOccurredAt() {
        return occurredAt;
    }

    public String getClientSessionId() {
        return clientSessionId;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public String getServerRequestId() {
        return serverRequestId;
    }

    public String getTurnId() {
        return turnId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public ErrorKind getErrorKind() {
        return errorKind;
    }

    public String getErrorFingerprint() {
        return errorFingerprint;
    }

    public DurationBucket getDurationBucket() {
        return durationBucket;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public GenerationMode getGenerationMode() {
        return generationMode;
    }

    public Boolean getDegraded() {
        return degraded;
    }

    public GuidanceStage getGuidanceStage() {
        return guidanceStage;
    }

    public Integer getSuggestedQuestionCount() {
        return suggestedQuestionCount;
    }

    public String getContentVersion() {
        return contentVersion;
    }

    public Integer getRecoveredCount() {
        return recoveredCount;
    }

    public enum ErrorKind {
        HTTP,
        TIMEOUT,
        NETWORK,
        INVALID_RESPONSE,
        CANCELLED,
        ERROR_EVENT,
        UNHANDLED_REJECTION
    }

    public enum DurationBucket {
        LT_1000_MS,
        FROM_1000_TO_4999_MS,
        GE_5000_MS
    }

    public enum GenerationMode {
        DETERMINISTIC,
        MODEL,
        FALLBACK
    }

    public enum GuidanceStage {
        OPENING,
        DEEPENING,
        WRAP_UP,
        EXPLORE_OTHERS
    }
}
