package com.portfolio.agent.common.observability;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 应用启动诊断记录器：在应用就绪及内容包/向量模型加载完成或失败时发布结构化 diagnostic 事件，
 * 供运维侧通过日志确认启动配置与关键资源状态。
 *
 * <p>关键不变量：所有事件发布均为 best-effort，诊断失败绝不影响启动主流程；
 * 事件只包含配置型字段（开关、数量、限额、耗时分桶），不携带访客数据或内部实现细节。
 * 构造参数在启动期校验，非法 retrieval profile 或非正数限额立即失败（fail-fast）。</p>
 */
public final class ApplicationStartupDiagnostics
        implements ApplicationListener<ApplicationReadyEvent> {

    private static final Set<String> RETRIEVAL_PROFILES = Set.of(
            "DISABLED",
            "KEYWORD_ONLY",
            "HYBRID");

    private final DiagnosticEventPublisher publisher;
    private final boolean modelRuntimeEnabled;
    private final int selectableModelCount;
    private final String retrievalProfile;
    private final long answerRequestTimeoutMillis;
    private final int answerRequestsPerMinute;
    private final int answerMaxConcurrent;

    public ApplicationStartupDiagnostics(
            DiagnosticEventPublisher publisher,
            boolean modelRuntimeEnabled,
            int selectableModelCount,
            String retrievalProfile,
            long answerRequestTimeoutMillis,
            int answerRequestsPerMinute,
            int answerMaxConcurrent
    ) {
        this.publisher = Objects.requireNonNull(
                publisher, "diagnostic event publisher must not be null");
        if (!RETRIEVAL_PROFILES.contains(retrievalProfile)) {
            throw new IllegalArgumentException("unsupported retrieval profile");
        }
        if (selectableModelCount < 0
                || answerRequestTimeoutMillis <= 0
                || answerRequestsPerMinute <= 0
                || answerMaxConcurrent <= 0) {
            throw new IllegalArgumentException(
                    "answer startup diagnostic values must be positive");
        }
        this.modelRuntimeEnabled = modelRuntimeEnabled;
        this.selectableModelCount = selectableModelCount;
        this.retrievalProfile = retrievalProfile;
        this.answerRequestTimeoutMillis = answerRequestTimeoutMillis;
        this.answerRequestsPerMinute = answerRequestsPerMinute;
        this.answerMaxConcurrent = answerMaxConcurrent;
    }

    /**
     * 记录公开内容包加载成功事件：包含 schema/内容版本、检索开关、文档数、向量维度与加载耗时分桶。
     *
     * @param elapsedMillis 加载耗时（毫秒），仅以分桶形式发布，避免暴露精确耗时细节
     */
    public void contentBundleLoaded(
            String schemaVersion,
            String contentVersion,
            boolean retrievalEnabled,
            int documentCount,
            int vectorDimension,
            long elapsedMillis
    ) {
        publishBestEffort(() -> DiagnosticEvent.builder(
                        "content.bundle.loaded", DiagnosticLevel.INFO)
                .field("schema.version", schemaVersion)
                .field("content.version", contentVersion)
                .field("retrieval.enabled", retrievalEnabled)
                .field("document.count", documentCount)
                .field("vector.dimension", vectorDimension)
                .field("duration.bucket", durationBucket(elapsedMillis))
                .build());
    }

    /**
     * 记录公开内容包加载失败事件（ERROR 级，failure.code = CONTENT_BUNDLE_INVALID）。
     */
    public void contentBundleFailed() {
        publishFailure(
                "application.startup.failed",
                StartupFailureCode.CONTENT_BUNDLE_INVALID);
    }

    /**
     * 记录本地 embedding 模型加载成功事件：包含向量维度与加载耗时分桶。
     */
    public void embeddingModelLoaded(int vectorDimension, long elapsedMillis) {
        publishBestEffort(() -> DiagnosticEvent.builder(
                        "embedding.model.loaded", DiagnosticLevel.INFO)
                .field("vector.dimension", vectorDimension)
                .field("duration.bucket", durationBucket(elapsedMillis))
                .build());
    }

    /**
     * 记录检索模型加载失败事件（ERROR 级，failure.code = RETRIEVAL_MODEL_LOAD_FAILED）。
     */
    public void embeddingModelFailed() {
        publishFailure(
                "embedding.model.failed",
                StartupFailureCode.RETRIEVAL_MODEL_LOAD_FAILED);
    }

    /**
     * 应用就绪回调：发布 application.started 事件，汇总模型运行时开关、可选模型数、
     * 检索 profile 与答案请求的超时/限流/并发配置，作为启动配置快照。
     *
     * @param event Spring 应用就绪事件（未使用其内容）
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        publishBestEffort(() -> DiagnosticEvent.builder(
                        "application.started", DiagnosticLevel.INFO)
                .field("model_runtime.enabled", modelRuntimeEnabled)
                .field("model_catalog.selectable_count", selectableModelCount)
                .field("retrieval.profile", retrievalProfile)
                .field("answer.request_timeout_ms", answerRequestTimeoutMillis)
                .field("answer.requests_per_minute", answerRequestsPerMinute)
                .field("answer.max_concurrent", answerMaxConcurrent)
                .build());
    }

    /**
     * 以 ERROR 级发布指定名称的启动失败事件，并附上稳定的 failure.code。
     */
    private void publishFailure(String eventName, StartupFailureCode failureCode) {
        publishBestEffort(() -> DiagnosticEvent.builder(eventName, DiagnosticLevel.ERROR)
                .field("failure.code", failureCode)
                .build());
    }

    /**
     * 将精确耗时映射为粗粒度分桶标识，避免在诊断事件中暴露精确性能数值。
     */
    private String durationBucket(long elapsedMillis) {
        if (elapsedMillis < 100) {
            return "LT_100_MS";
        }
        if (elapsedMillis < 500) {
            return "FROM_100_TO_499_MS";
        }
        if (elapsedMillis < 2000) {
            return "FROM_500_TO_1999_MS";
        }
        return "GE_2000_MS";
    }

    /**
     * best-effort 发布事件：吞掉发布过程中的运行时异常，保证诊断失败绝不中断启动流程。
     */
    private void publishBestEffort(Supplier<DiagnosticEvent> eventFactory) {
        try {
            publisher.publish(eventFactory.get());
        } catch (RuntimeException ignored) {
            // Diagnostic publication must never change startup behavior.
        }
    }

    /**
     * 启动失败错误码枚举：内容包非法与检索模型加载失败两类启动期故障的稳定标识。
     */
    public enum StartupFailureCode implements DiagnosticCode {
        CONTENT_BUNDLE_INVALID,
        RETRIEVAL_MODEL_LOAD_FAILED;

        @Override
        public String code() {
            return name();
        }
    }
}
