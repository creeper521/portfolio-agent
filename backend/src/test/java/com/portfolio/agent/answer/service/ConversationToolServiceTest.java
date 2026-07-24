package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.domain.ConversationRoute;
import com.portfolio.agent.answer.domain.ConversationSubjectOption;
import com.portfolio.agent.answer.domain.ConversationToolPlan;
import com.portfolio.agent.answer.domain.ConversationWindow;
import com.portfolio.agent.answer.domain.PortfolioGroundingContext;
import com.portfolio.agent.answer.domain.PortfolioKnowledgeFacet;
import com.portfolio.agent.answer.domain.PublicToolResult;
import com.portfolio.agent.answer.domain.PublicToolResultStatus;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.domain.ToolCall;
import com.portfolio.agent.answer.domain.ToolKind;
import com.portfolio.agent.answer.gateway.ConversationalModelPort;
import com.portfolio.agent.answer.gateway.PublicKnowledgeTools;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConversationToolServiceTest {

    private final ConversationalModelPort modelPort = mock(ConversationalModelPort.class);
    private final PublicKnowledgeTools tools = mock(PublicKnowledgeTools.class);
    private final ConversationToolService service =
            new ConversationToolService(modelPort, tools, 2, 4);

    @Test
    void generalKnowledgeNeverCallsPortfolioTools() {
        PortfolioGroundingContext result = service.enrich(
                content(),
                "什么是责任链",
                new ConversationWindow(null, List.of(), 0),
                route(ConversationIntent.GENERAL_KNOWLEDGE),
                PortfolioGroundingContext.empty());

        assertThat(result.getClaims()).isEmpty();
        verifyNoInteractions(modelPort, tools);
    }

    @Test
    void stopsAfterFourWhitelistedCalls() {
        List<ToolCall> calls = List.of(
                call(ToolKind.GET_PROJECT),
                call(ToolKind.GET_CLAIMS),
                call(ToolKind.GET_EVIDENCE_FOR_CLAIMS),
                call(ToolKind.GET_TIMELINE),
                call(ToolKind.SEARCH_PUBLIC_CONTENT));
        when(modelPort.planTools(anyString(), any(), any(), any(), anyList(), anyList()))
                .thenReturn(ConversationModelResult.success(
                        new ConversationToolPlan(calls)));
        when(tools.execute(any(), any())).thenAnswer(invocation ->
                emptyResult(invocation.getArgument(1, ToolCall.class).getKind()));

        service.enrich(
                content(),
                "具体怎么实现",
                new ConversationWindow(null, List.of(), 0),
                route(ConversationIntent.PORTFOLIO_GROUNDED),
                grounding());

        verify(tools, times(4)).execute(any(), any());
    }

    private ToolCall call(ToolKind kind) {
        return new ToolCall(kind, List.of("sql-audit"), List.of(), List.of(), null);
    }

    private ConversationRoute route(ConversationIntent intent) {
        boolean portfolio = intent == ConversationIntent.PORTFOLIO_GROUNDED;
        return new ConversationRoute(
                intent,
                portfolio
                        ? ConversationAnswerScope.PORTFOLIO
                        : ConversationAnswerScope.GENERAL,
                1.0,
                portfolio ? "sql-audit" : null,
                null,
                PortfolioKnowledgeFacet.IMPLEMENTATION,
                false);
    }

    private PortfolioGroundingContext grounding() {
        return new PortfolioGroundingContext(
                new ConversationSubjectOption(
                        com.portfolio.agent.answer.domain.AnswerSubjectType.PROJECT,
                        "sql-audit",
                        "SQL Audit",
                        "summary"),
                List.of(),
                List.of(),
                List.of());
    }

    private RuntimeAnswerContent content() {
        AnswerKnowledge project = new AnswerKnowledge(
                "sql-audit", "SQL Audit", "summary", "background",
                List.of(), "solution", List.of(), List.of(), "outcome",
                "handoff", "DELIVERED", List.of(), List.of(), List.of());
        return new RuntimeAnswerContent("v1", "hash", List.of(project));
    }

    private PublicToolResult emptyResult(ToolKind kind) {
        return new PublicToolResult(
                kind,
                "v1",
                "hash",
                PublicToolResultStatus.SUCCESS,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }
}
