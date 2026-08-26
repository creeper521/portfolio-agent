package com.portfolio.agent.infrastructure.model.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.infrastructure.model.policy.ModelOperationPolicyRegistry;
import com.portfolio.agent.infrastructure.model.provider.ModelCatalogSnapshot;
import com.portfolio.agent.infrastructure.model.structured.StructuredOutputContractRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 模型基础设施装配：在 Spring 启动时构建配置驱动的目录与冻结的目录快照 Bean。
 *
 * <p>目录与快照在此一次性构建并冻结，运行期不再重建；总开关与各 Provider
 * 准入全部由 {@link ConfiguredModelCatalog} 在构造期 fail-closed 地裁决。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        ModelRuntimeProperties.class
})
public class ModelInfrastructureConfiguration {

    /** 构建配置驱动的模型目录（含内部描述符与服务端传输绑定）。 */
    @Bean
    StructuredOutputContractRegistry structuredOutputContractRegistry(
            ObjectMapper objectMapper) {
        return StructuredOutputContractRegistry.standard(objectMapper);
    }

    @Bean
    ConfiguredModelCatalog configuredModelCatalog(
            ModelRuntimeProperties properties,
            ModelOperationPolicyRegistry operationPolicies,
            StructuredOutputContractRegistry contracts) {
        return new ConfiguredModelCatalog(properties, operationPolicies, contracts);
    }

    /** 把目录快照单独暴露为 Bean，供解析器与公开投影只读消费。 */
    @Bean
    ModelCatalogSnapshot modelCatalogSnapshot(ConfiguredModelCatalog catalog) {
        return catalog.snapshot();
    }

}
