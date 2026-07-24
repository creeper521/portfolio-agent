package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.adapter.model.ConversationalAgentConfiguration;
import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.dto.request.AnswerRequestSource;
import com.portfolio.agent.answer.dto.request.AudienceRole;
import com.portfolio.agent.answer.dto.request.ConversationAnswerContextRequest;
import com.portfolio.agent.answer.dto.request.ConversationAnswerRequest;
import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConversationalAgentRuntimeTest {

    @Test
    void greetingStillWorksNaturallyWhenProviderIsDisabled() {
        PortfolioKnowledgeGateway knowledgeGateway = mock(PortfolioKnowledgeGateway.class);
        when(knowledgeGateway.getContent()).thenReturn(
                new RuntimeAnswerContent("v1", "hash", List.of()));
        ConversationIntentRouter router = mock(ConversationIntentRouter.class);
        ConversationalAgentRuntime runtime = new ConversationalAgentRuntime(
                knowledgeGateway,
                mock(ConversationWindowManager.class),
                router,
                mock(PortfolioGroundingAssembler.class),
                mock(ConversationToolService.class),
                mock(com.portfolio.agent.answer.gateway.ConversationalModelPort.class),
                mock(ConversationDraftValidator.class),
                mock(DynamicQuestionService.class),
                new DeterministicConversationFallback(),
                new ConversationalAgentConfiguration.ConversationProviderAccess(false));

        ConversationAnswerResult result = runtime.answer(request("你好"));

        assertThat(result.getResolution()).isEqualTo(AnswerResolution.ANSWERED);
        assertThat(result.getIntent()).isEqualTo(ConversationIntent.CONVERSATION);
        assertThat(result.getAnswerScope()).isEqualTo(ConversationAnswerScope.CONVERSATION);
        assertThat(result.isDegraded()).isFalse();
        assertThat(result.getBlocks()).hasSize(1);
        verifyNoInteractions(router);
    }

    private ConversationAnswerRequest request(String question) {
        return new ConversationAnswerRequest(
                "turn-1",
                question,
                List.of(),
                new ConversationAnswerContextRequest(
                        null,
                        null,
                        AudienceRole.INTERVIEWER,
                        AnswerRequestSource.AGENT_PAGE));
    }
}
