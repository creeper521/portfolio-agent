package com.portfolio.agent.infrastructure.model.policy;

import java.time.Duration;
import java.util.Objects;

/**
 * 单个模型 Operation 的不可变启动权威：模式、schema 版本、输出预算与超时。
 *
 * <p>这是三重准入中第三重的运行期形态：即使 model-runtime 与 Provider 均已
 * 准入，某 Operation 若未显式 ENABLED 且补全 schema/预算/超时，依然不可执行
 * （fail-closed）。策略随启动冻结，运行期不重新加载。
 */
public final class ModelOperationPolicy {
    private final ModelOperation operation;
    private final OperationMode mode;
    private final String schemaVersion;
    private final int maxOutputTokens;
    private final Duration timeout;

    public ModelOperationPolicy(
            ModelOperation operation,
            OperationMode mode,
            String schemaVersion,
            int maxOutputTokens,
            Duration timeout) {
        this.operation = Objects.requireNonNull(operation, "operation");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.schemaVersion = schemaVersion;
        this.maxOutputTokens = maxOutputTokens;
        this.timeout = timeout;
    }

    public ModelOperation getOperation() { return operation; }
    public OperationMode getMode() { return mode; }
    public String getSchemaVersion() { return schemaVersion; }
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public Duration getTimeout() { return timeout; }

    /**
     * 评估该 Operation 的就绪度：DISABLED 模式直接判 DISABLED；
     * 模式为 ENABLED 但 schema 版本缺失/空白、输出预算小于 1、
     * 超时缺失或非正数时判 INCOMPLETE_CONFIGURATION；
     * 其余判 AVAILABLE_WITH_PROVIDER（仍需 Provider 侧准入）。
     */
    public OperationReadiness readiness() {
        if (mode == OperationMode.DISABLED) {
            return OperationReadiness.DISABLED;
        }
        if (schemaVersion == null
                || schemaVersion.isBlank()
                || maxOutputTokens < 1
                || timeout == null
                || timeout.isZero()
                || timeout.isNegative()) {
            return OperationReadiness.INCOMPLETE_CONFIGURATION;
        }
        return OperationReadiness.AVAILABLE_WITH_PROVIDER;
    }

    /**
     * 启动期校验：声明为 ENABLED 的 Operation 若配置不完整
     * （就绪度为 INCOMPLETE_CONFIGURATION）直接抛出 IllegalStateException，
     * 阻止应用以"看似启用实则残缺"的状态启动。
     */
    public void validateStartup() {
        if (mode == OperationMode.ENABLED
                && readiness() == OperationReadiness.INCOMPLETE_CONFIGURATION) {
            throw new IllegalStateException(
                    "enabled model operation is incomplete: " + operation);
        }
    }
}
