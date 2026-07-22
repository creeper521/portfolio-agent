package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.FollowUpIntent;
import com.portfolio.agent.answer.domain.QueryIntent;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.domain.ToolCall;
import com.portfolio.agent.answer.domain.ToolKind;
import com.portfolio.agent.answer.domain.ToolPlan;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public final class ToolPlanBuilder {

    private static final String TOOL_POLICY_VERSION = "c2b-tools-v1";

    public ToolPlan build(
            RuntimeAnswerContent content,
            QueryIntent queryIntent,
            int maxToolCalls
    ) {
        List<ToolKind> kinds = kindsFor(queryIntent.getFollowUpIntent());
        if (kinds.size() > maxToolCalls) {
            throw new IllegalArgumentException("tool call budget is insufficient");
        }
        List<ToolCall> calls = kinds.stream()
                .map(kind -> new ToolCall(
                        kind,
                        queryIntent.getProjectSlugs(),
                        queryIntent.getReferencedClaimIds(),
                        queryIntent.getSelectedSectionType()))
                .toList();
        return new ToolPlan(
                TOOL_POLICY_VERSION,
                content.getContentVersion(),
                content.getRuntimeBundleHash(),
                queryIntent,
                calls);
    }

    private List<ToolKind> kindsFor(FollowUpIntent intent) {
        return switch (intent) {
            case SHOW_EVIDENCE -> List.of(ToolKind.GET_EVIDENCE_FOR_CLAIMS);
            case EXPAND_SECTION -> List.of(
                    ToolKind.GET_PROJECT,
                    ToolKind.GET_CLAIMS,
                    ToolKind.GET_EVIDENCE_FOR_CLAIMS);
            case EXPLAIN_DECISION -> List.of(
                    ToolKind.GET_CLAIMS,
                    ToolKind.GET_EVIDENCE_FOR_CLAIMS);
            case CURRENT_STATUS -> List.of(
                    ToolKind.GET_PROJECT,
                    ToolKind.GET_CLAIMS,
                    ToolKind.GET_TIMELINE);
            case RELATED_QUESTION -> List.of(ToolKind.SEARCH_PUBLIC_CONTENT);
            case COMPARE_PROJECTS -> List.of(ToolKind.COMPARE_PROJECTS);
        };
    }
}
