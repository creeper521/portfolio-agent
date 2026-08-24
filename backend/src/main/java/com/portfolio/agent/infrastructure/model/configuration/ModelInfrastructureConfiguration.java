package com.portfolio.agent.infrastructure.model.configuration;

import com.portfolio.agent.infrastructure.model.provider.ModelCatalogSnapshot;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        ModelRuntimeProperties.class
})
public class ModelInfrastructureConfiguration {

    @Bean
    ConfiguredModelCatalog configuredModelCatalog(ModelRuntimeProperties properties) {
        return new ConfiguredModelCatalog(properties);
    }

    @Bean
    ModelCatalogSnapshot modelCatalogSnapshot(ConfiguredModelCatalog catalog) {
        return catalog.snapshot();
    }

}
