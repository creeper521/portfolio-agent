package com.portfolio.agent.turn.capability.general;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.domain.ConversationProviderAccess;
import com.portfolio.agent.answer.runtime.ModelOperation;
import com.portfolio.agent.answer.runtime.ModelOperationPolicy;
import com.portfolio.agent.answer.runtime.ModelOperationPolicyRegistry;
import com.portfolio.agent.answer.runtime.OperationMode;
import com.portfolio.agent.turn.execution.TurnDeadline;
import com.portfolio.agent.turn.planning.UserGoalProposal;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

final class GeneralTestFixtures {
    static final String VALID_EXPLANATION = """
            {"topic":"并发控制","statements":[
              {"role":"DEFINITION","text":"并发控制协调同时发生的工作。"},
              {"role":"MECHANISM","text":"它通过有界调度与状态隔离控制竞争。"}
            ],"caveats":["具体机制取决于运行环境。"]}
            """;
    private GeneralTestFixtures() { }

    static GeneralKnowledgeRequest explanation() {
        return GeneralKnowledgeRequest.explanation(
                "并发控制", UserGoalProposal.Depth.STANDARD,
                GeneralKnowledgeRequest.Audience.GUEST, "public-1",
                TurnDeadline.after(Duration.ofSeconds(5), Clock.systemUTC()));
    }

    static GeneralKnowledgeGenerator generator(GeneralKnowledgeModelPort port) {
        return new GeneralKnowledgeGenerator(
                new ConversationProviderAccess(true),
                new ModelOperationPolicyRegistry(Map.of(
                        ModelOperation.GENERAL_KNOWLEDGE,
                        new ModelOperationPolicy(
                                ModelOperation.GENERAL_KNOWLEDGE, OperationMode.ENABLED,
                                "provider", "general-v1", Duration.ofSeconds(2)))),
                port, new GeneralDraftCodec(new ObjectMapper()), new GeneralDraftValidator());
    }
}
