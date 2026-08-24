package com.portfolio.agent.infrastructure.model.configuration;

import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.policy.ModelOperationPolicy;
import com.portfolio.agent.infrastructure.model.policy.ModelOperationPolicyRegistry;
import com.portfolio.agent.infrastructure.model.policy.OperationMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/** Independent operation configuration; no operation inherits another operation's approval. */
@ConfigurationProperties(prefix = "portfolio.model-operations")
public final class ModelOperationProperties {
    private Settings turnInterpretation = new Settings(1600, Duration.ofSeconds(8));
    private Settings generalKnowledge = new Settings(1200, Duration.ofSeconds(10));

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
