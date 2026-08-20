package com.portfolio.agent.infrastructure.model.configuration;

import com.portfolio.agent.infrastructure.model.policy.ModelOperation;
import com.portfolio.agent.infrastructure.model.policy.ModelOperationPolicy;
import com.portfolio.agent.infrastructure.model.policy.ModelOperationPolicyRegistry;
import com.portfolio.agent.infrastructure.model.policy.OperationMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.Map;

/** Independent operation configuration; no operation inherits another operation's approval. */
@ConfigurationProperties(prefix = "portfolio.model-operations")
public final class ModelOperationProperties {
    private Settings turnInterpretation = new Settings();
    private Settings generalKnowledge = new Settings();

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
        private String providerRef;
        private String schemaVersion;

        private ModelOperationPolicy policy(ModelOperation operation) {
            return new ModelOperationPolicy(operation, mode, providerRef, schemaVersion);
        }

        public OperationMode getMode() { return mode; }
        public void setMode(OperationMode value) { mode = value; }
        public String getProviderRef() { return providerRef; }
        public void setProviderRef(String value) { providerRef = value; }
        public String getSchemaVersion() { return schemaVersion; }
        public void setSchemaVersion(String value) { schemaVersion = value; }
    }
}
