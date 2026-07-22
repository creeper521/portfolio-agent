package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AgentExecutionSnapshot;
import com.portfolio.agent.answer.domain.AnswerTurnSnapshot;
import com.portfolio.agent.answer.domain.ExecutionBudgets;
import com.portfolio.agent.answer.domain.ModelPolicy;
import com.portfolio.agent.answer.domain.RetrievalCapability;
import com.portfolio.agent.answer.domain.RetrievalPolicy;
import com.portfolio.agent.answer.gateway.ModelProviderRegistry;
import org.springframework.stereotype.Component;

@Component
public final class AgentExecutionSnapshotFactory {

    private static final String AUDIENCE_PROFILE_VERSION = "c1-audience-v1";
    private static final long TOTAL_DEADLINE_MS = 5000L;

    private final ModelPolicy modelPolicy;
    private final RetrievalPolicy retrievalPolicy;
    private final RetrievalCapability retrievalCapability;
    private final ModelProviderRegistry modelProviderRegistry;

    public AgentExecutionSnapshotFactory(
            ModelPolicy modelPolicy,
            RetrievalPolicy retrievalPolicy,
            RetrievalCapability retrievalCapability,
            ModelProviderRegistry modelProviderRegistry
    ) {
        this.modelPolicy = modelPolicy;
        this.retrievalPolicy = retrievalPolicy;
        this.retrievalCapability = retrievalCapability;
        this.modelProviderRegistry = modelProviderRegistry;
    }

    public AgentExecutionSnapshot create(AnswerTurnSnapshot turnSnapshot) {
        return new AgentExecutionSnapshot(
                turnSnapshot,
                AUDIENCE_PROFILE_VERSION,
                modelPolicy.getModelPolicyVersion(),
                modelPolicy.getAnswerSchemaVersion(),
                modelPolicy.isModelEnabled(),
                retrievalPolicy.getVersion(),
                retrievalCapability.isGroundedQuestionsEnabled(),
                retrievalCapability.getMode(),
                "c2b-tools-v1",
                true,
                true,
                modelProviderRegistry.getSnapshotVersion(),
                new ExecutionBudgets(
                        TOTAL_DEADLINE_MS,
                        modelPolicy.getMaxModelAttempts(),
                        4,
                        retrievalPolicy.getMaxClaims(),
                        retrievalPolicy.getMaxContextCharacters())
        );
    }
}
