package com.portfolio.agent.infrastructure.model.configuration;

import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.policy.ModelOperationPolicy;
import com.portfolio.agent.infrastructure.model.policy.ModelOperationPolicyRegistry;
import com.portfolio.agent.infrastructure.model.policy.OperationMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * 模型 Operation 独立配置：每个 Operation 各自持有模式、schema 版本、
 * 输出预算与超时。
 *
 * <p>三重准入的第三重（Operation 精确 schema/输出预算/超时）由此承载：
 * 任一 Operation 的批准与其他 Operation 完全独立，没有继承、没有默认开启；
 * 未显式配置的 Operation 保持 DISABLED。缺省值仅提供预算与超时建议，
 * mode 与 schemaVersion 仍需显式配置。
 */
@ConfigurationProperties(prefix = "portfolio.model-operations")
public final class ModelOperationProperties {
    private Settings turnInterpretation = new Settings(1600, Duration.ofSeconds(8));
    private Settings generalKnowledge = new Settings(1200, Duration.ofSeconds(10));

    /**
     * 把配置折算为 Operation 策略注册表。
     *
     * @return 覆盖全部 {@link ModelOperation} 的策略注册表，
     *         未显式启用的 Operation 以 DISABLED 策略进入注册表
     */
    public ModelOperationPolicyRegistry toRegistry() {
        Map<ModelOperation, ModelOperationPolicy> policies = new EnumMap<>(ModelOperation.class);
        policies.put(ModelOperation.TURN_INTERPRETATION, turnInterpretation.policy(ModelOperation.TURN_INTERPRETATION));
        policies.put(ModelOperation.GENERAL_KNOWLEDGE, generalKnowledge.policy(ModelOperation.GENERAL_KNOWLEDGE));
        return new ModelOperationPolicyRegistry(policies);
    }

    public Settings getTurnInterpretation() { return turnInterpretation; }
    public void setTurnInterpretation(Settings value) { turnInterpretation = value; }
    public Settings getGeneralKnowledge() { return generalKnowledge; }
    public void setGeneralKnowledge(Settings value) { generalKnowledge = value; }

    /** 单个 Operation 的配置载体：模式、schema 版本、输出 token 预算与超时。 */
    public static final class Settings {
        private OperationMode mode = OperationMode.DISABLED;
        private String schemaVersion;
        private int maxOutputTokens;
        private Duration timeout;

        public Settings() { }

        private Settings(int maxOutputTokens, Duration timeout) {
            this.maxOutputTokens = maxOutputTokens;
            this.timeout = timeout;
        }

        private ModelOperationPolicy policy(ModelOperation operation) {
            return new ModelOperationPolicy(
                    operation, mode, schemaVersion, maxOutputTokens, timeout);
        }

        public OperationMode getMode() { return mode; }
        public void setMode(OperationMode value) { mode = value; }
        public String getSchemaVersion() { return schemaVersion; }
        public void setSchemaVersion(String value) { schemaVersion = value; }
        public int getMaxOutputTokens() { return maxOutputTokens; }
        public void setMaxOutputTokens(int value) { maxOutputTokens = value; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration value) { timeout = value; }
    }
}
