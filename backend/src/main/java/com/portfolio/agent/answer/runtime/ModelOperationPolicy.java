package com.portfolio.agent.answer.runtime;
import java.time.Duration;
public final class ModelOperationPolicy {
    private final ModelOperation operation; private final OperationMode mode; private final String providerRef; private final String schemaVersion; private final Duration timeout;
    public ModelOperationPolicy(ModelOperation operation,OperationMode mode,String providerRef,String schemaVersion,Duration timeout){this.operation=java.util.Objects.requireNonNull(operation);this.mode=java.util.Objects.requireNonNull(mode);this.providerRef=providerRef;this.schemaVersion=schemaVersion;this.timeout=timeout;}
    public ModelOperation getOperation(){return operation;} public OperationMode getMode(){return mode;} public String getProviderRef(){return providerRef;} public String getSchemaVersion(){return schemaVersion;} public Duration getTimeout(){return timeout;}
    public OperationReadiness readiness(){if(mode==OperationMode.DISABLED)return OperationReadiness.DISABLED;if(providerRef==null||providerRef.isBlank()||schemaVersion==null||schemaVersion.isBlank()||timeout==null||timeout.isNegative()||timeout.isZero())return OperationReadiness.INCOMPLETE_CONFIGURATION;return OperationReadiness.AVAILABLE_WITH_DETERMINISTIC_FALLBACK;}
    public void validateStartup(){if(mode==OperationMode.ENABLED&&readiness()==OperationReadiness.INCOMPLETE_CONFIGURATION)throw new IllegalStateException("enabled model operation is incomplete: "+operation);}
}
