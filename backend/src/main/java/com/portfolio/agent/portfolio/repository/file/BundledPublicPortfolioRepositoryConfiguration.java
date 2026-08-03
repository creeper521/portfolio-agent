package com.portfolio.agent.portfolio.repository.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.common.observability.ApplicationStartupDiagnostics;
import com.portfolio.agent.portfolio.repository.PublicPortfolioRepository;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "portfolio.database.public",
        name = "enabled",
        havingValue = "true")
public class BundledPublicPortfolioRepositoryConfiguration {

    @Bean(name = "bundledPublicPortfolioRepository")
    PublicPortfolioRepository bundledPublicPortfolioRepository(
            ObjectMapper objectMapper,
            @Value("classpath:public-data/bundle/manifest.json") Resource manifest,
            @Value("classpath:public-data/bundle/portfolio.json") Resource portfolio,
            @Value("classpath:public-data/bundle/presentation.json") Resource presentation,
            @Value("classpath:public-data/bundle/rag-documents.jsonl") Resource ragDocuments,
            @Value("classpath:public-data/bundle/keyword-index.json") Resource keywordIndex,
            @Value("classpath:public-data/bundle/vector-index.bin") Resource vectorIndex,
            @Value("classpath:public-data/bundle/checksums.json") Resource checksums,
            PortfolioSnapshotValidator validator,
            ApplicationStartupDiagnostics startupDiagnostics) {
        return new JsonPublicPortfolioRepository(
                objectMapper,
                manifest,
                portfolio,
                presentation,
                ragDocuments,
                keywordIndex,
                vectorIndex,
                checksums,
                "",
                validator,
                startupDiagnostics);
    }
}
