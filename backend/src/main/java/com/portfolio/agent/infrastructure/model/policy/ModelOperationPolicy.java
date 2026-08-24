package com.portfolio.agent.infrastructure.model.policy;
public final class ModelOperationPolicy {
    private final ModelOperation operation; private final OperationMode mode; private final String providerRef; private final String schemaVersion;
    public ModelOperationPolicy(ModelOperation operation,OperationMode mode,String providerRef,String schemaVersion){this.operation=java.util.Objects.requireNonNull(operation);this.mode=java.util.Objects.requireNonNull(mode);this.providerRef=providerRef;this.schemaVersion=schemaVersion;}
    public ModelOperation getOperation(){return operation;} public OperationMode getMode(){return mode;} public String getProviderRef(){return providerRef;} public String getSchemaVersion(){return schemaVersion;}
    public OperationReadiness readiness(){if(mode==OperationMode.DISABLED)return OperationReadiness.DISABLED;if(providerRef==null||providerRef.isBlank()||schemaVersion==null||schemaVersion.isBlank())return OperationReadiness.INCOMPLETE_CONFIGURATION;return OperationReadiness.AVAILABLE_WITH_PROVIDER;}
    public void validateStartup(){if(mode==OperationMode.ENABLED&&readiness()==OperationReadiness.INCOMPLETE_CONFIGURATION)throw new IllegalStateException("enabled model operation is incomplete: "+operation);}
}
