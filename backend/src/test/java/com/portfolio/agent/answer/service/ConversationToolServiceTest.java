package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.domain.ConversationModelFailureCode;
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
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.DiagnosticLevel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
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
            new ConversationToolService(modelPort, tools, 2, 4, event -> { });

    @Test
    void requiresPublisherAndKeepsItFinalWithClosedDiagnosticResultStatus() throws Exception {
        assertThat(ConversationToolService.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(
                                ConversationalModelPort.class,
                                PublicKnowledgeTools.class,
                                int.class,
                                int.class,
                                DiagnosticEventPublisher.class));
        assertThat(Modifier.isFinal(
                ConversationToolService.class
                        .getDeclaredField("diagnosticEventPublisher")
                        .getModifiers())).isTrue();

        Method publisherMethod = Arrays.stream(
                        ConversationToolService.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("publishPlanResult"))
                .findFirst()
                .orElseThrow();
        assertThat(publisherMethod.getParameterTypes())
                .extracting(Class::getSimpleName)
                .contains("ToolDiagnosticResultStatus")
                .doesNotContain("String");
    }

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

    @Test
    void successfulPlanPublishesOnlyClosedPlanMetadata() {
        List<DiagnosticEvent> events = new ArrayList<>();
        ConversationToolService diagnosticService = service(events::add);
        when(modelPort.planTools(anyString(), any(), any(), any(), anyList(), anyList()))
                .thenReturn(ConversationModelResult.success(
                        new ConversationToolPlan(List.of())));

        diagnosticService.enrich(
                content(),
                "PRIVATE_PLAN_QUERY_SENTINEL",
                new ConversationWindow(null, List.of(), 0),
                route(ConversationIntent.PORTFOLIO_GROUNDED),
                grounding());

        assertThat(events).hasSize(2);
        DiagnosticEvent event = events.getFirst();
        assertThat(event.getName()).isEqualTo("tool.plan.completed");
        assertThat(event.getLevel()).isEqualTo(DiagnosticLevel.DEBUG);
        assertThat(event.getFields())
                .containsEntry("tool.round", 1)
                .containsEntry("tool.planned_call_count", 0)
                .containsEntry("tool.result_status", "SUCCESS")
                .containsOnlyKeys(
                        "tool.round",
                        "tool.allowed_count",
                        "tool.planned_call_count",
                        "tool.result_status",
                        "duration.bucket");
        assertThat(event.getFields().toString())
                .doesNotContain(
                        "PRIVATE_PLAN_QUERY_SENTINEL",
                        "arguments",
                        "results",
                        "content");
    }

    @Test
    void failedPlanPublishesMappedProviderFailureWithoutPlanContent() {
        List<DiagnosticEvent> events = new ArrayList<>();
        ConversationToolService diagnosticService = service(events::add);
        when(modelPort.planTools(anyString(), any(), any(), any(), anyList(), anyList()))
                .thenReturn(ConversationModelResult.failure(
                        ConversationModelFailureCode.TIMEOUT));

        PortfolioGroundingContext result = diagnosticService.enrich(
                content(),
                "PRIVATE_FAILED_PLAN_QUERY",
                new ConversationWindow(null, List.of(), 0),
                route(ConversationIntent.PORTFOLIO_GROUNDED),
                grounding());

        assertThat(result.getClaims()).isEmpty();
        assertThat(events).hasSize(1);
        DiagnosticEvent event = events.getFirst();
        assertThat(event.getName()).isEqualTo("tool.plan.completed");
        assertThat(event.getLevel()).isEqualTo(DiagnosticLevel.WARN);
        assertThat(event.getFields())
                .containsEntry("tool.round", 1)
                .containsEntry("tool.planned_call_count", 0)
                .containsEntry("tool.result_status", "FAILURE")
                .containsEntry("failure.code", "PROVIDER_TIMEOUT")
                .containsOnlyKeys(
                        "tool.round",
                        "tool.allowed_count",
                        "tool.planned_call_count",
                        "tool.result_status",
                        "duration.bucket",
                        "failure.code");
        assertThat(event.getFields().toString())
                .doesNotContain("PRIVATE_FAILED_PLAN_QUERY");
    }

    @Test
    void thrownPlanFailurePublishesExactlyOneWarningAndRethrowsSameException() {
        List<DiagnosticEvent> events = new ArrayList<>();
        ConversationToolService diagnosticService = service(events::add);
        IllegalStateException planFailure =
                new IllegalStateException("PRIVATE_PLAN_FAILURE_SENTINEL");
        when(modelPort.planTools(anyString(), any(), any(), any(), anyList(), anyList()))
                .thenThrow(planFailure);

        Throwable thrown = catchThrowable(() -> diagnosticService.enrich(
                content(),
                "PRIVATE_THROWING_PLAN_QUERY",
                new ConversationWindow(null, List.of(), 0),
                route(ConversationIntent.PORTFOLIO_GROUNDED),
                grounding()));

        assertThat(thrown).isSameAs(planFailure);
        assertThat(events).hasSize(1);
        DiagnosticEvent event = events.getFirst();
        assertThat(event.getName()).isEqualTo("tool.plan.completed");
        assertThat(event.getLevel()).isEqualTo(DiagnosticLevel.WARN);
        assertThat(event.getFields())
                .containsEntry("tool.result_status", "FAILURE")
                .containsEntry("failure.code", "PROVIDER_INVALID_RESPONSE");
        assertThat(event.getFields().toString())
                .doesNotContain(
                        "PRIVATE_PLAN_FAILURE_SENTINEL",
                        "PRIVATE_THROWING_PLAN_QUERY");
    }

    @Test
    void thrownToolExecutionPublishesExactlyOneCallWarningAndRethrowsSameException() {
        List<DiagnosticEvent> events = new ArrayList<>();
        ConversationToolService diagnosticService = service(event -> {
            events.add(event);
            if ("tool.call.completed".equals(event.getName())) {
                throw new IllegalStateException("publisher unavailable");
            }
        });
        when(modelPort.planTools(anyString(), any(), any(), any(), anyList(), anyList()))
                .thenReturn(ConversationModelResult.success(
                        new ConversationToolPlan(List.of(call(ToolKind.GET_PROJECT)))));
        IllegalStateException executionFailure =
                new IllegalStateException("PRIVATE_TOOL_EXECUTION_SENTINEL");
        when(tools.execute(any(), any())).thenThrow(executionFailure);

        Throwable thrown = catchThrowable(() -> diagnosticService.enrich(
                content(),
                "PRIVATE_THROWING_TOOL_QUERY",
                new ConversationWindow(null, List.of(), 0),
                route(ConversationIntent.PORTFOLIO_GROUNDED),
                grounding()));

        assertThat(thrown).isSameAs(executionFailure);
        assertThat(events).hasSize(2);
        assertThat(events.get(0).getName()).isEqualTo("tool.plan.completed");
        DiagnosticEvent failureEvent = events.get(1);
        assertThat(failureEvent.getName()).isEqualTo("tool.call.completed");
        assertThat(failureEvent.getLevel()).isEqualTo(DiagnosticLevel.WARN);
        assertThat(failureEvent.getFields())
                .containsEntry("tool.kind", "GET_PROJECT")
                .containsEntry("tool.result_status", "FAILURE")
                .containsEntry("failure.code", "TOOL_EXECUTION_FAILED");
        assertThat(failureEvent.getFields().toString())
                .doesNotContain(
                        "PRIVATE_TOOL_EXECUTION_SENTINEL",
                        "PRIVATE_THROWING_TOOL_QUERY");
    }

    @Test
    void publisherFailureDoesNotChangeToolPlanningResult() {
        ConversationToolService diagnosticService = service(event -> {
            throw new IllegalStateException("publisher unavailable");
        });
        when(modelPort.planTools(anyString(), any(), any(), any(), anyList(), anyList()))
                .thenReturn(ConversationModelResult.failure(
                        ConversationModelFailureCode.TIMEOUT));

        PortfolioGroundingContext result = diagnosticService.enrich(
                content(),
                "具体怎么实现",
                new ConversationWindow(null, List.of(), 0),
                route(ConversationIntent.PORTFOLIO_GROUNDED),
                grounding());

        assertThat(result.getClaims()).isEmpty();
    }

    private ToolCall call(ToolKind kind) {
        return new ToolCall(kind, List.of("sql-audit"), List.of(), List.of(), null);
    }

    private ConversationToolService service(DiagnosticEventPublisher publisher) {
        return new ConversationToolService(modelPort, tools, 2, 4, publisher);
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
