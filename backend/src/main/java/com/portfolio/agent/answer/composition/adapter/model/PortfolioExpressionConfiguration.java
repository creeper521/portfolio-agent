package com.portfolio.agent.answer.composition.adapter.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.adapter.model.ModelProviderDescriptor;
import com.portfolio.agent.answer.adapter.model.ModelProviderRegistrySnapshot;
import com.portfolio.agent.answer.composition.codec.PortfolioExpressionDraftCodec;
import com.portfolio.agent.answer.composition.gateway.PortfolioExpressionPort;
import com.portfolio.agent.answer.composition.projection.ModelExpressionInputProjector;
import com.portfolio.agent.answer.composition.service.DeterministicPortfolioAnswerComposer;
import com.portfolio.agent.answer.composition.service.ExpressionCircuitBreaker;
import com.portfolio.agent.answer.composition.service.ModelExpressionEligibilityPolicy;
import com.portfolio.agent.answer.composition.service.PortfolioAnswerPlanValidator;
import com.portfolio.agent.answer.composition.service.PortfolioCompositionDiagnostics;
import com.portfolio.agent.answer.composition.validation.FactDraftValidator;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.core.env.Environment;
import com.portfolio.agent.answer.composition.service.PortfolioAnswerComposition;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PortfolioExpressionProperties.class)
public class PortfolioExpressionConfiguration {

    @Bean
    PortfolioExpressionStartupValidator portfolioExpressionStartupValidator() {
        return new PortfolioExpressionStartupValidator();
    }

    @Bean
    PortfolioExpressionStartupGuard portfolioExpressionStartupGuard(
            PortfolioExpressionProperties properties,
            PortfolioExpressionStartupValidator validator,
            Environment environment,
            ObjectProvider<ModelProviderRegistrySnapshot> registryProvider) {
        boolean production = environment.acceptsProfiles("prod") || environment.acceptsProfiles("production");
        ModelProviderRegistrySnapshot registry = registryProvider.getIfAvailable(ModelProviderRegistrySnapshot::builtIn);
        boolean registryCompatible = registry.supports(
                properties.getProvider(), properties.getPolicyVersion(), properties.getInputSchemaVersion())
                && registry.supports(
                        properties.getProvider(), properties.getPolicyVersion(), properties.getDraftSchemaVersion());
        boolean schemaSupported = "portfolio-expression-input.v1".equals(properties.getInputSchemaVersion())
                && "portfolio-expression-draft.v1".equals(properties.getDraftSchemaVersion());
        ModelProviderDescriptor descriptor = registry.getRequiredDescriptor(properties.getProvider());
        validator.validate(properties, production, properties.selectedApiKey(),
                descriptor.getEndpoint().toString(),
                registryCompatible, schemaSupported);
        return new PortfolioExpressionStartupGuard();
    }

    @Bean
    @ConditionalOnProperty(prefix = "portfolio.model-expression", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(PortfolioExpressionPort.class)
    PortfolioExpressionPort portfolioExpressionPort(
            PortfolioExpressionProperties properties,
            ModelProviderRegistrySnapshot registry,
            ObjectMapper objectMapper,
            DiagnosticEventPublisher diagnosticEventPublisher) {
        ModelProviderDescriptor descriptor = registry.getRequiredDescriptor(properties.getProvider());
        HttpClient client = HttpClient.newBuilder().connectTimeout(properties.getTimeout()).build();
        return new OpenAiCompatiblePortfolioExpressionAdapter(
                new JdkPortfolioExpressionTransport(client, objectMapper),
                new PortfolioExpressionPromptFactory(), objectMapper,
                descriptor.getEndpoint().toString(), properties.selectedApiKey(),
                descriptor.getModelName(), properties.getMaxOutputTokens(),
                properties.getTimeout(), Clock.systemUTC(),
                new PortfolioExpressionDiagnostics(diagnosticEventPublisher));
    }

    @Bean
    PortfolioAnswerComposition portfolioAnswerComposition(
            PortfolioExpressionProperties properties,
            ObjectProvider<PortfolioExpressionPort> expressionPortProvider,
            DiagnosticEventPublisher diagnosticEventPublisher) {
        return new PortfolioAnswerComposition(
                new DeterministicPortfolioAnswerComposer(),
                new PortfolioAnswerPlanValidator(),
                expressionPortProvider.getIfAvailable(),
                new ModelExpressionInputProjector(),
                new PortfolioExpressionDraftCodec(),
                new ExpressionCircuitBreaker(Clock.systemUTC()),
                new FactDraftValidator(),
                new ModelExpressionEligibilityPolicy(),
                Clock.systemUTC(), properties.isEnabled()
                        && properties.getAllowedMaterialKinds().contains(
                                com.portfolio.agent.answer.composition.domain.MaterialKind.FACT),
                new PortfolioCompositionDiagnostics(diagnosticEventPublisher));
    }
}
