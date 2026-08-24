package com.portfolio.agent.common.observability;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

public final class DiagnosticEvent {

    private static final int SCHEMA_VERSION = 1;
    private static final Pattern EVENT_NAME_PATTERN = Pattern.compile(
            "[a-z][a-z0-9]*(\\.[a-z][a-z0-9_]*)+");
    private static final Pattern CAMEL_CASE_BOUNDARY_PATTERN = Pattern.compile(
            "(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");
    private static final Pattern NON_ALPHANUMERIC_PATTERN = Pattern.compile("[^a-z0-9]+");
    private static final Set<String> APPROVED_ANSWER_FIELD_KEYS = Set.of(
            "answer.resolution",
            "answer.source",
            "answer.scope",
            "answer.request_timeout_ms",
            "answer.requests_per_minute",
            "answer.max_concurrent",
            "question.kind");
    private static final Set<String> FORBIDDEN_FIELD_TOKEN_ROOTS = Set.of(
            "question",
            "message",
            "answer",
            "prompt",
            "payload",
            "header",
            "body",
            "credential",
            "authorization",
            "cookie");
    private static final Set<String> HTTP_BASE_FIELDS = fields(
            "trace.id",
            "request.id",
            "client.session.id",
            "client.request.id",
            "turn.id",
            "http.method",
            "http.route");
    private static final Set<String> FRONTEND_FIELDS = fields(
            "event.origin",
            "client.session.id",
            "client.request.id",
            "client.reported_request_id",
            "turn.id",
            "error.code",
            "error.kind",
            "error.fingerprint",
            "duration.bucket",
            "http.status_code",
            "generation.mode",
            "guidance.stage",
            "suggestion.count",
            "content.version",
            "recovery.count");
    private static final Map<String, Set<String>> APPROVED_FIELDS_BY_EVENT =
            Map.ofEntries(
                    Map.entry("application.started", fields(
                            "model_expression.enabled",
                            "conversation.enabled",
                            "retrieval.profile",
                            "answer.request_timeout_ms",
                            "answer.requests_per_minute",
                            "answer.max_concurrent")),
                    Map.entry("application.startup.failed", fields("failure.code")),
                    Map.entry("content.bundle.loaded", fields(
                            "schema.version",
                            "content.version",
                            "retrieval.enabled",
                            "document.count",
                            "vector.dimension",
                            "duration.bucket")),
                    Map.entry("embedding.model.loaded", fields(
                            "vector.dimension",
                            "duration.bucket")),
                    Map.entry("embedding.model.failed", fields("failure.code")),
                    Map.entry("http.request.started", HTTP_BASE_FIELDS),
                    Map.entry("http.request.completed", union(
                            HTTP_BASE_FIELDS,
                            "http.status_code",
                            "duration.ms",
                            "event.outcome")),
                    Map.entry("http.request.rejected", union(
                            HTTP_BASE_FIELDS,
                            "http.status_code",
                            "duration.ms",
                            "error.code")),
                    Map.entry("http.request.failed", union(
                            HTTP_BASE_FIELDS,
                            "http.status_code",
                            "duration.ms",
                            "error.code",
                            "failure.exception_type",
                            "failure.frames")),
                    Map.entry("agent.route.decided", fields(
                            "conversation.intent",
                            "answer.scope",
                            "route.source",
                            "duration.bucket")),
                    Map.entry("retrieval.completed", fields(
                            "retrieval.requested_mode",
                            "retrieval.actual_mode",
                            "retrieval.decision",
                            "retrieval.keyword_hit_count",
                            "retrieval.vector_hit_count",
                            "retrieval.fused_candidate_count",
                            "retrieval.accepted_chunk_count",
                            "duration.bucket")),
                    Map.entry("retrieval.fallback", fields(
                            "retrieval.requested_mode",
                            "retrieval.actual_mode",
                            "retrieval.decision",
                            "retrieval.keyword_hit_count",
                            "retrieval.vector_hit_count",
                            "retrieval.fused_candidate_count",
                            "retrieval.accepted_chunk_count",
                            "duration.bucket",
                            "failure.code")),
                    Map.entry("tool.plan.completed", fields(
                            "tool.round",
                            "tool.allowed_count",
                            "tool.planned_call_count",
                            "tool.result_status",
                            "duration.bucket",
                            "failure.code")),
                    Map.entry("tool.call.completed", fields(
                            "tool.kind",
                            "tool.result_status",
                            "tool.claim_count",
                            "tool.evidence_count",
                            "duration.bucket",
                            "failure.code")),
                    Map.entry("provider.call.completed", fields(
                            "provider.operation",
                            "event.outcome",
                            "duration.bucket",
                            "response.present")),
                    Map.entry("provider.call.failed", fields(
                            "provider.operation",
                            "event.outcome",
                            "duration.bucket",
                            "response.present",
                            "failure.code",
                            "failure.layer")),
                    Map.entry("provider.output.rejected", fields(
                            "provider.operation",
                            "failure.code",
                            "failure.layer",
                            "failure.reason")),
                    Map.entry("expression.eligibility", fields(
                            "task.kind", "material.kind", "expression.disposition",
                            "expression.attempted", "input.size.bucket", "breaker.state")),
                    Map.entry("expression.provider.completed", fields(
                            "provider.operation", "event.outcome", "duration.bucket",
                            "response.present", "output.size.bucket")),
                    Map.entry("expression.provider.failed", fields(
                            "provider.operation", "event.outcome", "duration.bucket",
                            "response.present", "failure.code")),
                    Map.entry("expression.validation.completed", fields(
                            "material.kind", "validation.accepted", "failure.code",
                            "section.count.bucket", "sentence.count.bucket")),
                    Map.entry("expression.fallback.used", fields(
                            "expression.disposition", "failure.code", "breaker.state",
                            "expression.fallback")),
                    Map.entry("answer.validation.completed", fields(
                            "validation.accepted",
                            "failure.code",
                            "duration.bucket")),
                    Map.entry("answer.fallback.selected", fields(
                            "fallback.trigger",
                            "failure.code")),
                    Map.entry("portfolio.intelligence.completed", fields(
                            "task.mode",
                            "subject.count",
                            "evidence.count",
                            "recommendation.count",
                            "context.present",
                            "validation.result",
                            "duration.bucket")),
                    Map.entry("portfolio.execution.failed", fields(
                            "failure.stage",
                            "failure.code",
                            "capability.code",
                            "task.type")),
                    Map.entry("semantic.turn.completed", fields(
                            "plan.task.count",
                            "plan.task.succeeded.count",
                            "plan.task.blocked.count",
                            "plan.task.failed.count",
                            "plan.outcome",
                            "plan.disposition")),
                    Map.entry("agent.request.completed", fields(
                            "content.version",
                            "question.kind",
                            "audience.role",
                            "request.source",
                            "conversation.intent",
                            "answer.scope",
                            "answer.resolution",
                            "answer.source",
                            "generation.mode",
                            "verification.status",
                            "duration.bucket",
                            "error.code")),
                    Map.entry("frontend.application.started", FRONTEND_FIELDS),
                    Map.entry("frontend.content.load.completed", FRONTEND_FIELDS),
                    Map.entry("frontend.agent.request.completed", FRONTEND_FIELDS),
                    Map.entry("frontend.content.load.failed", FRONTEND_FIELDS),
                    Map.entry("frontend.agent.request.failed", FRONTEND_FIELDS),
                    Map.entry("frontend.agent.request.slow", FRONTEND_FIELDS),
                    Map.entry("frontend.agent.request.cancelled", FRONTEND_FIELDS),
                    Map.entry("frontend.response.invalid", FRONTEND_FIELDS),
                    Map.entry("frontend.runtime.failed", FRONTEND_FIELDS));

    private final String name;
    private final DiagnosticLevel level;
    private final Map<String, Object> fields;

    private DiagnosticEvent(String name, DiagnosticLevel level, Map<String, Object> fields) {
        this.name = name;
        this.level = level;
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public static Builder builder(String name, DiagnosticLevel level) {
        validateEventName(name);
        if (!APPROVED_FIELDS_BY_EVENT.containsKey(name)) {
            throw new IllegalArgumentException(
                    "unsupported diagnostic event name: " + name);
        }
        return new Builder(name, Objects.requireNonNull(level, "diagnostic level must not be null"));
    }

    public int getSchemaVersion() {
        return SCHEMA_VERSION;
    }

    public String getName() {
        return name;
    }

    public DiagnosticLevel getLevel() {
        return level;
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    void forEachApprovedField(BiConsumer<String, Object> consumer) {
        BiConsumer<String, Object> requiredConsumer = Objects.requireNonNull(
                consumer, "consumer must not be null");
        fields.forEach((key, value) -> {
            validateFieldKey(name, key);
            requiredConsumer.accept(key, value);
        });
    }

    private static void validateEventName(String name) {
        if (name == null || !EVENT_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("invalid diagnostic event name: " + name);
        }
    }

    private static void validateFieldKey(String eventName, String key) {
        if (key != null && APPROVED_ANSWER_FIELD_KEYS.contains(key)) {
            validateApprovedEventField(eventName, key);
            return;
        } else if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("diagnostic field key must not be blank");
        }
        if (key.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("diagnostic field key contains control character: " + key);
        }
        String lowercaseKey = key.toLowerCase(Locale.ROOT);
        String normalizedAlphanumericKey = NON_ALPHANUMERIC_PATTERN.matcher(lowercaseKey)
                .replaceAll("");
        for (String forbiddenRoot : FORBIDDEN_FIELD_TOKEN_ROOTS) {
            if (normalizedAlphanumericKey.contains(forbiddenRoot)) {
                throw new IllegalArgumentException("diagnostic field is forbidden: " + key);
            }
        }
        if (normalizedAlphanumericKey.contains("rawip")) {
            throw new IllegalArgumentException("diagnostic field is forbidden: " + key);
        }
        String camelSeparatedKey = CAMEL_CASE_BOUNDARY_PATTERN.matcher(key).replaceAll(".");
        String normalizedKey = camelSeparatedKey.toLowerCase(Locale.ROOT);
        String[] fieldTokens = NON_ALPHANUMERIC_PATTERN.split(normalizedKey);
        for (String fieldToken : fieldTokens) {
            if (fieldToken.equals("ip")) {
                throw new IllegalArgumentException("diagnostic field is forbidden: " + key);
            }
        }
        validateApprovedEventField(eventName, key);
    }

    private static void validateApprovedEventField(String eventName, String key) {
        Set<String> approvedFields = APPROVED_FIELDS_BY_EVENT.get(eventName);
        if (approvedFields == null) {
            throw new IllegalArgumentException(
                    "unsupported diagnostic event name: " + eventName);
        }
        if (!approvedFields.contains(key)) {
            throw new IllegalArgumentException(
                    "diagnostic field is not approved for " + eventName + ": " + key);
        }
    }

    private static Set<String> fields(String... values) {
        return Set.of(values);
    }

    private static Set<String> union(Set<String> base, String... additional) {
        LinkedHashMap<String, Boolean> values = new LinkedHashMap<>();
        for (String value : base) {
            values.put(value, Boolean.TRUE);
        }
        for (String value : additional) {
            values.put(value, Boolean.TRUE);
        }
        return Set.copyOf(values.keySet());
    }

    private static Object approvedScalarValue(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("diagnostic field value must not be null");
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof String || value instanceof Boolean || isApprovedNumber(value)) {
            return value;
        }
        if (value instanceof Iterable<?> || value instanceof Map<?, ?> || value.getClass().isArray()) {
            throw new IllegalArgumentException("diagnostic field value must be a scalar");
        }
        throw new IllegalArgumentException("diagnostic field value type is not allowed: "
                + value.getClass().getName());
    }

    private static boolean isApprovedNumber(Object value) {
        return value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double
                || value instanceof BigInteger
                || value instanceof BigDecimal;
    }

    public static final class Builder {

        private final String name;
        private final DiagnosticLevel level;
        private final Map<String, Object> fields = new LinkedHashMap<>();

        private Builder(String name, DiagnosticLevel level) {
            this.name = name;
            this.level = level;
        }

        public Builder field(String key, Object value) {
            validateFieldKey(name, key);
            fields.put(key, approvedScalarValue(value));
            return this;
        }

        public DiagnosticEvent build() {
            return new DiagnosticEvent(name, level, fields);
        }
    }
}
