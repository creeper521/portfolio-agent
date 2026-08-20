package com.portfolio.agent.infrastructure.model.policy;
import java.util.EnumMap; import java.util.Map;
public final class ModelOperationPolicyRegistry { private final Map<ModelOperation,ModelOperationPolicy> policies;
    public ModelOperationPolicyRegistry(Map<ModelOperation,ModelOperationPolicy> policies){this.policies=new EnumMap<>(policies);for(ModelOperation op:ModelOperation.values())this.policies.putIfAbsent(op,new ModelOperationPolicy(op,OperationMode.DISABLED,null,null));this.policies.values().forEach(ModelOperationPolicy::validateStartup);}
    public static ModelOperationPolicyRegistry defaults(){Map<ModelOperation,ModelOperationPolicy> p=new EnumMap<>(ModelOperation.class);for(ModelOperation op:ModelOperation.values())p.put(op,new ModelOperationPolicy(op,OperationMode.DISABLED,null,null));return new ModelOperationPolicyRegistry(p);}
    public ModelOperationPolicy get(ModelOperation operation){return policies.get(operation);} public Map<ModelOperation,ModelOperationPolicy> asMap(){return Map.copyOf(policies);}
}
