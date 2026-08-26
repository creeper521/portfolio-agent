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

/**
 * 结构化诊断事件：整个可观测性体系的事件载体，通过事件名白名单与逐事件字段白名单
 * 双重校验，保证只有预先批准的有限字段集合能进入日志。
 *
 * <p>关键不变量（信息不泄漏边界）：
 * 事件名必须命中 {@link #APPROVED_FIELDS_BY_EVENT} 的封闭集合；字段键除命中白名单外，
 * 还会被禁止词根检查（question、message、answer、prompt、payload、header、body、
 * credential、authorization、cookie、rawip 及独立 ip 词元）拦截，防止访客问题、
 * 报文内容或来源地址借道诊断日志泄漏；字段值只允许标量（字符串、布尔、数字、枚举名），
 * 拒绝集合与数组。事件构建完成后字段 Map 只读，实例可安全共享。</p>
 *
 * <p>失败行为：builder 或 field 阶段任一校验不通过立即抛出 IllegalArgumentException（fail-fast），
 * 让越界字段在开发与测试期即暴露，而不是带病进入日志。</p>
 */
public final class DiagnosticEvent {

    private static final int SCHEMA_VERSION = 1;
    private static final Pattern EVENT_NAME_PATTERN = Pattern.compile(
            "[a-z][a-z0-9]*(\\.[a-z][a-z0-9_]*)+");
    private static final Pattern CAMEL_CASE_BOUNDARY_PATTERN = Pattern.compile(
            "(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");
    private static final Pattern NON_ALPHANUMERIC_PATTERN = Pattern.compile("[^a-z0-9]+");
    // 答案链路少量含 question/answer 词根的字段经单独批准后放行，其余一律被禁止词根拦截。
    private static final Set<String> APPROVED_ANSWER_FIELD_KEYS = Set.of(
            "answer.resolution",
            "answer.source",
            "answer.scope",
            "answer.request_timeout_ms",
            "answer.requests_per_minute",
            "answer.max_concurrent",
            "question.kind");
    // 禁止词根：任何字段键（去分隔符后）包含这些词根即视为可能携带访客内容或敏感信息，直接拒绝。
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
    // 封闭的事件-字段白名单：每个事件名只允许发布此处登记的字段键，新增字段必须先在此登记。
    private static final Map<String, Set<String>> APPROVED_FIELDS_BY_EVENT =
            Map.ofEntries(
                    Map.entry("application.started", fields(
                            "model_runtime.enabled",
                            "model_catalog.selectable_count",
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
                            "failure.layer",
                            "failure.reason")),
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

    /**
     * 为指定事件名与级别创建事件 Builder。
     *
     * @param name  事件名，须匹配小写点分命名格式且在事件白名单内
     * @param level 事件级别，不允许为 null
     * @return 可继续追加字段的 Builder
     * @throws IllegalArgumentException 当事件名为 null、格式非法或不在白名单时抛出
     */
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

    /**
     * 在输出前再次校验并遍历全部字段（包内可见，供日志发布器逐字段输出）。
     *
     * <p>重复执行字段键校验形成纵深防御：即使字段在构建后被外部注入，也会在输出前被拦截。</p>
     *
     * @param consumer 接收已批准字段键值的消费者，不允许为 null
     */
    void forEachApprovedField(BiConsumer<String, Object> consumer) {
        BiConsumer<String, Object> requiredConsumer = Objects.requireNonNull(
                consumer, "consumer must not be null");
        fields.forEach((key, value) -> {
            validateFieldKey(name, key);
            requiredConsumer.accept(key, value);
        });
    }

    /**
     * 校验事件名非空且符合小写点分命名格式（如 http.request.completed）。
     */
    private static void validateEventName(String name) {
        if (name == null || !EVENT_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("invalid diagnostic event name: " + name);
        }
    }

    /**
     * 字段键清洗算法：综合禁止词根检查与逐事件白名单，判定字段键能否发布。
     *
     * <p>流程：单独批准的答案链路字段直接走白名单校验；其余键依次检查非空、
     * 不含控制字符、去分隔符归一化后不含禁止词根（question/prompt/credential 等）、
     * 不含 rawip 变体、按 camelCase 与分隔符切分后不含独立 ip 词元，
     * 全部通过后才要求命中该事件的字段白名单。</p>
     *
     * @param eventName 已在白名单内的事件名
     * @param key       待校验的字段键
     * @throws IllegalArgumentException 当键为空白、含控制字符、命中任一禁止词根或不在该事件白名单时抛出
     */
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

    /**
     * 校验字段键确实登记在该事件的批准字段集合中。
     *
     * @throws IllegalArgumentException 当事件不在白名单或字段未获该事件批准时抛出
     */
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

    /**
     * 由可变参数构造字段键集合（白名单登记的便捷写法）。
     */
    private static Set<String> fields(String... values) {
        return Set.of(values);
    }

    /**
     * 合并基础字段集合与追加字段为一个新的不可变集合（保持首次出现顺序，仅用于白名单登记）。
     */
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

    /**
     * 值白名单校验：只放行非 null 的标量（枚举转为常量名，字符串/布尔/整数与浮点/大小数字面量直接放行），
     * 拒绝集合、数组与 Map，防止结构化内容整体混入日志。
     *
     * @throws IllegalArgumentException 当值为 null、非标量类型或不允许的类型时抛出
     */
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

    /**
     * 判断值是否属于批准的数值类型（含 BigInteger/BigDecimal 等大小数类型）。
     */
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

    /**
     * 事件构建器：逐字段追加并即时校验（键白名单 + 标量值校验），build 时冻结为不可变事件。
     */
    public static final class Builder {

        private final String name;
        private final DiagnosticLevel level;
        private final Map<String, Object> fields = new LinkedHashMap<>();

        private Builder(String name, DiagnosticLevel level) {
            this.name = name;
            this.level = level;
        }

        /**
         * 追加一个字段并即时完成键与值校验。
         *
         * @param key   已获该事件批准的字段键
         * @param value 非 null 的标量值，枚举自动转为常量名
         * @return 当前 Builder，支持链式调用
         * @throws IllegalArgumentException 当键或值未通过白名单校验时抛出
         */
        public Builder field(String key, Object value) {
            validateFieldKey(name, key);
            fields.put(key, approvedScalarValue(value));
            return this;
        }

        /**
         * 冻结当前字段并构建不可变事件；同键后写覆盖先写，构建后字段集合只读。
         */
        public DiagnosticEvent build() {
            return new DiagnosticEvent(name, level, fields);
        }
    }
}
