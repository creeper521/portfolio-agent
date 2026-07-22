package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AgentExecutionSnapshot;
import com.portfolio.agent.answer.domain.AnswerTurnSnapshot;
import com.portfolio.agent.answer.domain.ModelPolicy;
import com.portfolio.agent.answer.domain.ModelProviderKind;
import com.portfolio.agent.answer.domain.RetrievalCapability;
import com.portfolio.agent.answer.domain.RetrievalMode;
import com.portfolio.agent.answer.domain.RetrievalPolicy;
import com.portfolio.agent.answer.gateway.ModelProviderRegistry;
import com.portfolio.agent.answer.dto.request.AnswerRequestSource;
import com.portfolio.agent.answer.dto.request.AudienceRole;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentExecutionSnapshotFactoryTest {

    @Test
    void combinesTheExistingTurnSnapshotWithFixedC1PolicyAndBudgets() {
        AnswerTurnSnapshot turn = new AnswerTurnSnapshot(
                "memory-turn-id",
                "request-correlation-id",
                "2026-07-21.1",
                "sha256:runtime",
                "project-1",
                "preset-1",
                List.of("evidence-1"),
                AudienceRole.INTERVIEWER,
                AnswerRequestSource.AGENT_PAGE
        );
        ModelPolicy policy = new ModelPolicy(
                "c1-policy-v1",
                "c1.answer.v1",
                ModelProviderKind.GLM_4_7,
                true,
                true,
                true,
                Duration.ofSeconds(4),
                1200,
                1
        );

        RetrievalPolicy retrievalPolicy = RetrievalPolicy.firstRelease();
        AgentExecutionSnapshot snapshot = new AgentExecutionSnapshotFactory(
                policy, retrievalPolicy, RetrievalCapability.hybridEnabled(
                        "BAAI/bge-small-zh-v1.5", "sha256:model", 512),
                new TestModelProviderRegistry()).create(turn);

        assertThat(snapshot.getTurnSnapshot()).isSameAs(turn);
        assertThat(snapshot.getModelPolicyVersion()).isEqualTo("c1-policy-v1");
        assertThat(snapshot.getAnswerSchemaVersion()).isEqualTo("c1.answer.v1");
        assertThat(snapshot.getAudienceProfileVersion()).isEqualTo("c1-audience-v1");
        assertThat(snapshot.isModelExpressionEnabled()).isTrue();
        assertThat(snapshot.isGroundedQuestionsEnabled()).isTrue();
        assertThat(snapshot.getRetrievalMode()).isEqualTo(RetrievalMode.HYBRID_ENABLED);
        assertThat(snapshot.getRetrievalPolicyVersion())
                .isEqualTo(retrievalPolicy.getVersion());
        assertThat(snapshot.isReadOnlyToolsEnabled()).isTrue();
        assertThat(snapshot.isMultiTurnReferencesEnabled()).isTrue();
        assertThat(snapshot.getToolPolicyVersion()).isEqualTo("c2b-tools-v1");
        assertThat(snapshot.getRegistrySnapshotVersion())
                .isEqualTo("c3-model-registry-v1");
        assertThat(snapshot.getBudgets().getMaxToolCalls()).isEqualTo(4);
        assertThat(snapshot.getBudgets().getMaxModelAttempts()).isEqualTo(1);
        assertThat(snapshot.getBudgets().getMaxRetrievedClaims())
                .isEqualTo(retrievalPolicy.getMaxClaims());
        assertThat(snapshot.getBudgets().getMaxContextCharacters())
                .isEqualTo(retrievalPolicy.getMaxContextCharacters());
        assertThat(AgentExecutionSnapshot.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("question", "conversation", "history", "prompt", "draft",
                        "apiKey", "endpoint", "descriptor", "providerResponse");
    }

    private static final class TestModelProviderRegistry implements ModelProviderRegistry {

        @Override
        public String getSnapshotVersion() {
            return "c3-model-registry-v1";
        }

        @Override
        public boolean supports(
                ModelProviderKind provider, String policy, String schema) {
            return true;
        }
    }
}
