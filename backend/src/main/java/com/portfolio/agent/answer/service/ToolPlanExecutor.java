package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.ExecutionBudgets;
import com.portfolio.agent.answer.domain.FollowUpIntent;
import com.portfolio.agent.answer.domain.PublicToolResult;
import com.portfolio.agent.answer.domain.PublicToolResultStatus;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.domain.ToolCall;
import com.portfolio.agent.answer.domain.ToolExecutionOutcome;
import com.portfolio.agent.answer.domain.ToolPlan;
import com.portfolio.agent.answer.domain.ToolKind;
import com.portfolio.agent.answer.gateway.PublicKnowledgeTools;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.DiagnosticLevel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public final class ToolPlanExecutor {

    private final PublicKnowledgeTools tools;
    private final ToolResultValidator validator;
    private final DiagnosticEventPublisher diagnosticEventPublisher;

    public ToolPlanExecutor(
            PublicKnowledgeTools tools,
            ToolResultValidator validator,
            DiagnosticEventPublisher diagnosticEventPublisher
    ) {
        this.tools = tools;
        this.validator = validator;
        this.diagnosticEventPublisher = Objects.requireNonNull(
                diagnosticEventPublisher,
                "diagnosticEventPublisher");
    }

    public ToolExecutionOutcome execute(
            RuntimeAnswerContent content,
            ToolPlan plan,
            ExecutionBudgets budgets
    ) {
        if (!content.getContentVersion().equals(plan.getContentVersion())
                || !content.getRuntimeBundleHash().equals(plan.getRuntimeBundleHash())) {
            throw new IllegalArgumentException("tool plan snapshot does not match");
        }
        if (plan.getCalls().size() > budgets.getMaxToolCalls()) {
            throw new IllegalArgumentException("tool call budget is exceeded");
        }
        List<PublicToolResult> results = new ArrayList<>();
        PublicToolResultStatus status = PublicToolResultStatus.SUCCESS;
        for (ToolCall call : plan.getCalls()) {
            long startedAt = System.nanoTime();
            PublicToolResult result;
            try {
                result = tools.execute(content, call);
            } catch (RuntimeException exception) {
                publishToolCall(
                        call,
                        null,
                        ToolDiagnosticResultStatus.FAILURE,
                        ToolFailureCode.TOOL_EXECUTION_FAILED,
                        startedAt);
                throw exception;
            }
            try {
                validator.validate(content, call, result, budgets);
            } catch (RuntimeException exception) {
                publishToolCall(
                        call,
                        result,
                        ToolDiagnosticResultStatus.INVALID,
                        ToolFailureCode.TOOL_RESULT_INVALID,
                        startedAt);
                throw exception;
            }
            results.add(result);
            publishToolCall(
                    call,
                    result,
                    ToolDiagnosticResultStatus.fromPublicToolResultStatus(
                            result.getStatus()),
                    null,
                    startedAt);
            if (result.getStatus() == PublicToolResultStatus.INSUFFICIENT
                    && !isOptionalEvidenceGap(plan, call)) {
                status = PublicToolResultStatus.INSUFFICIENT;
            }
        }
        return new ToolExecutionOutcome(status, results);
    }

    private void publishToolCall(
            ToolCall call,
            PublicToolResult result,
            ToolDiagnosticResultStatus resultStatus,
            ToolFailureCode failureCode,
            long startedAt
    ) {
        DiagnosticEvent.Builder builder = DiagnosticEvent.builder(
                        "tool.call.completed",
                        failureCode == null
                                ? DiagnosticLevel.DEBUG
                                : DiagnosticLevel.WARN)
                .field("tool.kind", call.getKind())
                .field("tool.result_status", resultStatus)
                .field("tool.claim_count",
                        result == null ? 0 : result.getClaims().size())
                .field("tool.evidence_count",
                        result == null ? 0 : result.getEvidence().size())
                .field("duration.bucket", DurationBuckets.fromElapsedMillis(
                        (System.nanoTime() - startedAt) / 1_000_000L));
        if (failureCode != null) {
            builder.field("failure.code", failureCode.code());
        }
        publishBestEffort(builder.build());
    }

    private void publishBestEffort(DiagnosticEvent event) {
        try {
            diagnosticEventPublisher.publish(event);
        } catch (RuntimeException ignored) {
            // Diagnostics must never change Tool execution.
        }
    }

    private boolean isOptionalEvidenceGap(ToolPlan plan, ToolCall call) {
        if (call.getKind() != ToolKind.GET_EVIDENCE_FOR_CLAIMS) {
            return false;
        }
        FollowUpIntent intent = plan.getQueryIntent().getFollowUpIntent();
        return intent == FollowUpIntent.EXPAND_SECTION
                || intent == FollowUpIntent.EXPLAIN_DECISION;
    }
}
