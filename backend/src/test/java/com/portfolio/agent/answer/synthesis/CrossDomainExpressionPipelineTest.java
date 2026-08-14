package com.portfolio.agent.answer.synthesis;

import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.gateway.ConversationalModelPort;
import com.portfolio.agent.answer.runtime.ModelOperation;
import com.portfolio.agent.answer.runtime.ModelOperationPolicy;
import com.portfolio.agent.answer.runtime.ModelOperationPolicyRegistry;
import com.portfolio.agent.answer.runtime.OperationMode;
import com.portfolio.agent.answer.synthesis.domain.AllowedRelation;
import com.portfolio.agent.answer.synthesis.domain.RelationType;
import com.portfolio.agent.answer.synthesis.service.CrossDomainExpressionPipeline;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrossDomainExpressionPipelineTest {

    @Test
    void enabledExpressionUsesProviderOnlyAfterRelationAndAliasValidation() {
        ConversationalModelPort model = mock(ConversationalModelPort.class);
        when(model.generateCrossDomainExpression(anyString())).thenReturn(
                ConversationModelResult.success("""
                        {"schemaVersion":"cross-domain-expression-v1","sectionKind":"RELATION",
                         "text":"general — portfolio (ILLUSTRATES)","relationAlias":"relation-1",
                         "statementAliases":["general-task","portfolio-task"],"caveatAliases":[]}
                        """));
        CrossDomainExpressionPipeline pipeline = new CrossDomainExpressionPipeline(
                model, enabledRegistry());

        Optional<String> result = pipeline.express(
                "general", "portfolio", relation());

        assertThat(result).contains("general — portfolio (ILLUSTRATES)");
    }

    @Test
    void invalidProviderDraftIsAtomicAndReturnsDeterministicFallbackSignal() {
        ConversationalModelPort model = mock(ConversationalModelPort.class);
        when(model.generateCrossDomainExpression(anyString())).thenReturn(
                ConversationModelResult.success("""
                        {"schemaVersion":"cross-domain-expression-v1","sectionKind":"RELATION",
                         "text":"unsupported","relationAlias":"invented",
                         "statementAliases":["general-task","portfolio-task"],"caveatAliases":[]}
                        """));

        assertThat(new CrossDomainExpressionPipeline(model, enabledRegistry())
                .express("general", "portfolio", relation())).isEmpty();
    }

    @Test
    void matchingAliasesCannotAuthorizeMutationOfProtectedMaterial() {
        ConversationalModelPort model = mock(ConversationalModelPort.class);
        when(model.generateCrossDomainExpression(anyString())).thenReturn(
                ConversationModelResult.success("""
                        {"schemaVersion":"cross-domain-expression-v1","sectionKind":"RELATION",
                         "text":"general — invented portfolio claim (ILLUSTRATES)",
                         "relationAlias":"relation-1",
                         "statementAliases":["general-task","portfolio-task"],"caveatAliases":[]}
                        """));

        assertThat(new CrossDomainExpressionPipeline(model, enabledRegistry())
                .express("general", "portfolio", relation())).isEmpty();
    }

    private AllowedRelation relation() {
        return new AllowedRelation("relation-1", "general-task", "portfolio-task",
                RelationType.ILLUSTRATES, Set.of("IMPLEMENTATION"), Set.of());
    }

    private ModelOperationPolicyRegistry enabledRegistry() {
        Map<ModelOperation, ModelOperationPolicy> policies = new EnumMap<>(ModelOperation.class);
        for (ModelOperation operation : ModelOperation.values()) {
            policies.put(operation, new ModelOperationPolicy(
                    operation, OperationMode.DISABLED, null, null, null));
        }
        policies.put(ModelOperation.CROSS_DOMAIN_EXPRESSION, new ModelOperationPolicy(
                ModelOperation.CROSS_DOMAIN_EXPRESSION, OperationMode.ENABLED,
                "fake", "cross-domain-expression-v1", Duration.ofSeconds(1)));
        return new ModelOperationPolicyRegistry(policies);
    }
}
