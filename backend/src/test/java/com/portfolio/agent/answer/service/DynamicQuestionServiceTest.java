package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.domain.ConversationRoute;
import com.portfolio.agent.answer.domain.ConversationSuggestedQuestion;
import com.portfolio.agent.answer.domain.ConversationWindow;
import com.portfolio.agent.answer.domain.PortfolioKnowledgeFacet;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.gateway.ConversationalModelPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DynamicQuestionServiceTest {

    @Test
    void keepsOnlyDistinctQuestionsThatHaveGrounding() {
        ConversationalModelPort modelPort = mock(ConversationalModelPort.class);
        PortfolioGroundingAssembler assembler = mock(PortfolioGroundingAssembler.class);
        DynamicQuestionService service =
                new DynamicQuestionService(modelPort, assembler, 3);
        ConversationSuggestedQuestion implementation = suggestion(
                "具体是怎么实现的？", PortfolioKnowledgeFacet.IMPLEMENTATION);
        ConversationSuggestedQuestion duplicate = suggestion(
                "具体是怎么实现的？", PortfolioKnowledgeFacet.IMPLEMENTATION);
        ConversationSuggestedQuestion unsupported = suggestion(
                "当时发生过什么事故？", PortfolioKnowledgeFacet.INCIDENT);
        ConversationSuggestedQuestion verification = suggestion(
                "结果是如何验证的？", PortfolioKnowledgeFacet.VERIFICATION);
        when(modelPort.suggest(any(), any(), any(), any())).thenReturn(
                ConversationModelResult.success(List.of(
                        implementation, duplicate, unsupported, verification)));
        when(assembler.canAnswer(any(), any())).thenAnswer(invocation ->
                invocation.getArgument(1) != unsupported);

        List<ConversationSuggestedQuestion> result = service.generate(
                content(),
                route(),
                new ConversationWindow(null, List.of(), 0),
                List.of());

        assertThat(result)
                .extracting(ConversationSuggestedQuestion::getText)
                .containsExactly("具体是怎么实现的？", "结果是如何验证的？");
    }

    private ConversationSuggestedQuestion suggestion(
            String text,
            PortfolioKnowledgeFacet facet
    ) {
        return new ConversationSuggestedQuestion(
                text, "sql-audit", null, facet);
    }

    private ConversationRoute route() {
        return new ConversationRoute(
                ConversationIntent.PORTFOLIO_GROUNDED,
                ConversationAnswerScope.PORTFOLIO,
                1.0,
                "sql-audit",
                null,
                PortfolioKnowledgeFacet.OVERVIEW,
                false);
    }

    private RuntimeAnswerContent content() {
        AnswerKnowledge project = new AnswerKnowledge(
                "sql-audit", "SQL Audit", "summary", "background",
                List.of(), "solution", List.of(), List.of(), "outcome",
                "handoff", "DELIVERED", List.of(), List.of(), List.of());
        return new RuntimeAnswerContent("v1", "hash", List.of(project));
    }
}
