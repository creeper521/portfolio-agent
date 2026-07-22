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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolPlanExecutorTest {

    @Test
    void executesEveryCallAgainstTheExactCapturedContentInstance() {
        RuntimeAnswerContent content = new RuntimeAnswerContent(
                "2026-07-21.1", "sha256:runtime", List.of());
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
        ToolPlanExecutor executor = new ToolPlanExecutor(tools, new ToolResultValidator());

        ToolExecutionOutcome outcome = executor.execute(
                content, plan, new ExecutionBudgets(5000L, 1, 4, 8, 4000));

        assertThat(calls).hasValue(3);
        assertThat(outcome.getStatus()).isEqualTo(PublicToolResultStatus.INSUFFICIENT);
        assertThat(outcome.getResults()).hasSize(3);
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
                new ToolResultValidator());

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
        ToolPlanExecutor executor = new ToolPlanExecutor(tools, new ToolResultValidator());
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

    private static PublicToolResult insufficient(
            RuntimeAnswerContent content,
            ToolCall call
    ) {
        return new PublicToolResult(
                call.getKind(), content.getContentVersion(), content.getRuntimeBundleHash(),
                PublicToolResultStatus.INSUFFICIENT,
                List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
