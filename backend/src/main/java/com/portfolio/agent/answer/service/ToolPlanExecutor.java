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
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public final class ToolPlanExecutor {

    private final PublicKnowledgeTools tools;
    private final ToolResultValidator validator;

    public ToolPlanExecutor(PublicKnowledgeTools tools, ToolResultValidator validator) {
        this.tools = tools;
        this.validator = validator;
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
            PublicToolResult result = tools.execute(content, call);
            validator.validate(content, call, result, budgets);
            results.add(result);
            if (result.getStatus() == PublicToolResultStatus.INSUFFICIENT
                    && !isOptionalEvidenceGap(plan, call)) {
                status = PublicToolResultStatus.INSUFFICIENT;
            }
        }
        return new ToolExecutionOutcome(status, results);
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
