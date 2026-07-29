package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.ExecutionBudgets;
import com.portfolio.agent.answer.domain.FollowUpIntent;
import com.portfolio.agent.answer.domain.PublicToolResult;
import com.portfolio.agent.answer.domain.PublicToolResultStatus;
import com.portfolio.agent.answer.domain.QueryIntent;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.domain.ToolCall;
import com.portfolio.agent.answer.domain.ToolExecutionOutcome;
import com.portfolio.agent.answer.domain.ToolPlan;
import com.portfolio.agent.answer.domain.ToolKind;
import com.portfolio.agent.answer.gateway.PublicKnowledgeTools;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.DiagnosticLevel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class ToolPlanExecutorTest {

    @Test
    void requiresPublisherAndUsesClosedDiagnosticResultStatus() {
        assertThat(ToolDiagnosticResultStatus.values()).containsExactly(
                ToolDiagnosticResultStatus.SUCCESS,
                ToolDiagnosticResultStatus.INSUFFICIENT,
                ToolDiagnosticResultStatus.FAILURE,
                ToolDiagnosticResultStatus.INVALID);
        assertThat(ToolDiagnosticResultStatus.fromPublicToolResultStatus(
                PublicToolResultStatus.SUCCESS))
                .isEqualTo(ToolDiagnosticResultStatus.SUCCESS);
        assertThat(ToolDiagnosticResultStatus.fromPublicToolResultStatus(
                PublicToolResultStatus.INSUFFICIENT))
                .isEqualTo(ToolDiagnosticResultStatus.INSUFFICIENT);

        assertThat(ToolPlanExecutor.class.getDeclaredConstructors())
                .singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(
                                PublicKnowledgeTools.class,
                                ToolResultValidator.class,
                                DiagnosticEventPublisher.class));

        Method publisherMethod = Arrays.stream(
                        ToolPlanExecutor.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("publishToolCall"))
                .findFirst()
                .orElseThrow();
        assertThat(publisherMethod.getParameterTypes())
                .extracting(Class::getSimpleName)
                .contains("ToolDiagnosticResultStatus")
                .doesNotContain("String");
    }

    @Test
    void executesEveryCallAgainstTheExactCapturedContentInstance() {
        RuntimeAnswerContent content = new RuntimeAnswerContent(
                "2026-07-21.1", "sha256:runtime", List.of());
        List<DiagnosticEvent> events = new ArrayList<>();
        QueryIntent intent = new QueryIntent(
                FollowUpIntent.CURRENT_STATUS,
                List.of("sql-audit"),
                List.of("claim-1"),
                AnswerSectionType.STATUS);
        ToolPlan plan = new ToolPlanBuilder().build(content, intent, 4);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<RuntimeAnswerContent> seen = new AtomicReference<>();
        PublicKnowledgeTools tools = (captured, call) -> {
            calls.incrementAndGet();
            if (seen.get() == null) {
                seen.set(captured);
            }
            assertThat(captured).isSameAs(content).isSameAs(seen.get());
            return insufficient(captured, call);
        };
        ToolPlanExecutor executor = executor(tools, events::add);

        ToolExecutionOutcome outcome = executor.execute(
                content, plan, new ExecutionBudgets(5000L, 1, 4, 8, 4000));

        assertThat(calls).hasValue(3);
        assertThat(outcome.getStatus()).isEqualTo(PublicToolResultStatus.INSUFFICIENT);
        assertThat(outcome.getResults()).hasSize(3);
        assertThat(events).hasSize(3);
        DiagnosticEvent event = events.getFirst();
        assertThat(event.getName()).isEqualTo("tool.call.completed");
        assertThat(event.getFields())
                .containsEntry("tool.kind", "GET_PROJECT")
                .containsEntry("tool.result_status", "INSUFFICIENT")
                .containsEntry("tool.claim_count", 0)
                .containsEntry("tool.evidence_count", 0)
                .containsOnlyKeys(
                        "tool.kind",
                        "tool.result_status",
                        "tool.claim_count",
                        "tool.evidence_count",
                        "duration.bucket");
    }

    @Test
    void rejectsPlanBeforeExecutionWhenCallBudgetIsExceeded() {
        RuntimeAnswerContent content = new RuntimeAnswerContent(
                "2026-07-21.1", "sha256:runtime", List.of());
        QueryIntent intent = new QueryIntent(
                FollowUpIntent.EXPAND_SECTION,
                List.of("sql-audit"), List.of(), AnswerSectionType.SOLUTION);
        ToolPlan plan = new ToolPlanBuilder().build(content, intent, 4);
        ToolPlanExecutor executor = new ToolPlanExecutor(
                (captured, call) -> insufficient(captured, call),
                new ToolResultValidator(),
                event -> { });

        assertThatThrownBy(() -> executor.execute(
                content, plan, new ExecutionBudgets(5000L, 1, 2, 8, 4000)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tool call budget");
    }

    @Test
    void treatsMissingEvidenceAsOptionalForExpansionButRequiredForShowEvidence() {
        RuntimeAnswerContent content = new RuntimeAnswerContent(
                "2026-07-21.1", "sha256:runtime", List.of());
        PublicKnowledgeTools tools = (captured, call) -> new PublicToolResult(
                call.getKind(), captured.getContentVersion(), captured.getRuntimeBundleHash(),
                call.getKind() == ToolKind.GET_EVIDENCE_FOR_CLAIMS
                        ? PublicToolResultStatus.INSUFFICIENT
                        : PublicToolResultStatus.SUCCESS,
                List.of(), List.of(), List.of(), List.of(), List.of());
        ToolPlanExecutor executor = new ToolPlanExecutor(
                tools,
                new ToolResultValidator(),
                event -> { });
        ExecutionBudgets budgets = new ExecutionBudgets(5000L, 1, 4, 8, 4000);

        ToolPlan expand = new ToolPlanBuilder().build(content, new QueryIntent(
                FollowUpIntent.EXPAND_SECTION, List.of("sql-audit"),
                List.of("claim-1"), AnswerSectionType.SOLUTION), 4);
        ToolPlan showEvidence = new ToolPlanBuilder().build(content, new QueryIntent(
                FollowUpIntent.SHOW_EVIDENCE, List.of("sql-audit"),
                List.of("claim-1"), AnswerSectionType.SOLUTION), 4);

        assertThat(executor.execute(content, expand, budgets).getStatus())
                .isEqualTo(PublicToolResultStatus.SUCCESS);
        assertThat(executor.execute(content, showEvidence, budgets).getStatus())
                .isEqualTo(PublicToolResultStatus.INSUFFICIENT);
    }

    @Test
    void invalidResultPublishesSafeFailureAndRethrowsOriginalException() {
        RuntimeAnswerContent content = new RuntimeAnswerContent(
                "2026-07-21.1", "sha256:runtime", List.of());
        QueryIntent intent = new QueryIntent(
                FollowUpIntent.CURRENT_STATUS,
                List.of("sql-audit"),
                List.of("claim-1"),
                AnswerSectionType.STATUS);
        ToolPlan plan = new ToolPlanBuilder().build(content, intent, 4);
        List<DiagnosticEvent> events = new ArrayList<>();
        IllegalArgumentException validationFailure =
                new IllegalArgumentException("PRIVATE_VALIDATION_SENTINEL");
        ToolResultValidator validator = org.mockito.Mockito.mock(ToolResultValidator.class);
        org.mockito.Mockito.doThrow(validationFailure)
                .when(validator)
                .validate(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
        ToolPlanExecutor executor = executor(
                (captured, call) -> insufficient(captured, call),
                validator,
                event -> {
                    events.add(event);
                    throw new IllegalStateException("publisher unavailable");
                });

        Throwable thrown = catchThrowable(() -> executor.execute(
                content,
                plan,
                new ExecutionBudgets(5000L, 1, 4, 8, 4000)));

        assertThat(thrown).isSameAs(validationFailure);
        assertThat(events).hasSize(1);
        DiagnosticEvent event = events.getFirst();
        assertThat(event.getName()).isEqualTo("tool.call.completed");
        assertThat(event.getLevel()).isEqualTo(DiagnosticLevel.WARN);
        assertThat(event.getFields())
                .containsEntry("tool.kind", "GET_PROJECT")
                .containsEntry("tool.result_status", "INVALID")
                .containsEntry("failure.code", "TOOL_RESULT_INVALID")
                .containsOnlyKeys(
                        "tool.kind",
                        "tool.result_status",
                        "tool.claim_count",
                        "tool.evidence_count",
                        "duration.bucket",
                        "failure.code");
        assertThat(event.getFields().toString())
                .doesNotContain(
                        "PRIVATE_VALIDATION_SENTINEL",
                        "claim-1",
                        "evidence-1",
                        "arguments",
                        "results",
                        "content");
    }

    @Test
    void executionFailurePublishesExactlyOneWarningAndRethrowsSameException() {
        RuntimeAnswerContent content = new RuntimeAnswerContent(
                "2026-07-21.1", "sha256:runtime", List.of());
        QueryIntent intent = new QueryIntent(
                FollowUpIntent.CURRENT_STATUS,
                List.of("sql-audit"),
                List.of("claim-1"),
                AnswerSectionType.STATUS);
        ToolPlan plan = new ToolPlanBuilder().build(content, intent, 4);
        List<DiagnosticEvent> events = new ArrayList<>();
        IllegalStateException executionFailure =
                new IllegalStateException("PRIVATE_TOOL_EXECUTION_SENTINEL");
        ToolPlanExecutor executor = executor(
                (captured, call) -> {
                    throw executionFailure;
                },
                event -> {
                    events.add(event);
                    throw new IllegalStateException("publisher unavailable");
                });

        Throwable thrown = catchThrowable(() -> executor.execute(
                content,
                plan,
                new ExecutionBudgets(5000L, 1, 4, 8, 4000)));

        assertThat(thrown).isSameAs(executionFailure);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getName()).isEqualTo("tool.call.completed");
            assertThat(event.getLevel()).isEqualTo(DiagnosticLevel.WARN);
            assertThat(event.getFields())
                    .containsEntry("tool.kind", "GET_PROJECT")
                    .containsEntry("tool.result_status", "FAILURE")
                    .containsEntry("failure.code", "TOOL_EXECUTION_FAILED")
                    .containsOnlyKeys(
                            "tool.kind",
                            "tool.result_status",
                            "tool.claim_count",
                            "tool.evidence_count",
                            "duration.bucket",
                            "failure.code");
            assertThat(event.getFields().toString())
                    .doesNotContain("PRIVATE_TOOL_EXECUTION_SENTINEL");
        });
    }

    @Test
    void publisherFailureDoesNotChangeToolExecutionOutcome() {
        RuntimeAnswerContent content = new RuntimeAnswerContent(
                "2026-07-21.1", "sha256:runtime", List.of());
        QueryIntent intent = new QueryIntent(
                FollowUpIntent.CURRENT_STATUS,
                List.of("sql-audit"),
                List.of("claim-1"),
                AnswerSectionType.STATUS);
        ToolPlan plan = new ToolPlanBuilder().build(content, intent, 4);
        ToolPlanExecutor executor = executor(
                (captured, call) -> insufficient(captured, call),
                new ToolResultValidator(),
                event -> {
                    throw new IllegalStateException("publisher unavailable");
                });

        ToolExecutionOutcome outcome = executor.execute(
                content,
                plan,
                new ExecutionBudgets(5000L, 1, 4, 8, 4000));

        assertThat(outcome.getResults()).hasSize(3);
    }

    private static PublicToolResult insufficient(
            RuntimeAnswerContent content,
            ToolCall call
    ) {
        return new PublicToolResult(
                call.getKind(), content.getContentVersion(), content.getRuntimeBundleHash(),
                PublicToolResultStatus.INSUFFICIENT,
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private ToolPlanExecutor executor(
            PublicKnowledgeTools tools,
            DiagnosticEventPublisher publisher
    ) {
        return executor(tools, new ToolResultValidator(), publisher);
    }

    private ToolPlanExecutor executor(
            PublicKnowledgeTools tools,
            ToolResultValidator validator,
            DiagnosticEventPublisher publisher
    ) {
        return new ToolPlanExecutor(tools, validator, publisher);
    }
}
