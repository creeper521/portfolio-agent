package com.portfolio.agent.answer.adapter.portfolio;

import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import com.portfolio.agent.portfolio.repository.PublicPortfolioRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "portfolio.database.public",
        name = "enabled",
        havingValue = "true")
public class BundledPortfolioKnowledgeConfiguration {

    @Bean(name = "bundledPortfolioKnowledgeGateway")
    PortfolioKnowledgeGateway bundledPortfolioKnowledgeGateway(
            @Qualifier("bundledPublicPortfolioRepository")
            PublicPortfolioRepository repository) {
        return new LocalPortfolioKnowledgeAdapter(repository);
    }
}
