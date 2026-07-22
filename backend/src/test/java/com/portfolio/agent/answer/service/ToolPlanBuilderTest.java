package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.FollowUpIntent;
import com.portfolio.agent.answer.domain.QueryIntent;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.domain.ToolKind;
import com.portfolio.agent.answer.domain.ToolPlan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolPlanBuilderTest {

    private final ToolPlanBuilder builder = new ToolPlanBuilder();
    private final RuntimeAnswerContent content = new RuntimeAnswerContent(
            "2026-07-21.1", "sha256:runtime", List.of());

    @Test
    void mapsEveryClosedFollowUpIntentBeforeExecution() {
        assertKinds(FollowUpIntent.SHOW_EVIDENCE,
                ToolKind.GET_EVIDENCE_FOR_CLAIMS);
        assertKinds(FollowUpIntent.EXPAND_SECTION,
                ToolKind.GET_PROJECT,
                ToolKind.GET_CLAIMS,
                ToolKind.GET_EVIDENCE_FOR_CLAIMS);
        assertKinds(FollowUpIntent.EXPLAIN_DECISION,
                ToolKind.GET_CLAIMS,
                ToolKind.GET_EVIDENCE_FOR_CLAIMS);
        assertKinds(FollowUpIntent.CURRENT_STATUS,
                ToolKind.GET_PROJECT,
                ToolKind.GET_CLAIMS,
                ToolKind.GET_TIMELINE);
        assertKinds(FollowUpIntent.RELATED_QUESTION,
                ToolKind.SEARCH_PUBLIC_CONTENT);
        assertKinds(FollowUpIntent.COMPARE_PROJECTS,
                ToolKind.COMPARE_PROJECTS);
    }

    @Test
    void bindsPlanToOneRuntimeContentAndRejectsInsufficientBudget() {
        QueryIntent intent = intent(FollowUpIntent.EXPAND_SECTION);

        ToolPlan plan = builder.build(content, intent, 4);

        assertThat(plan.getToolPolicyVersion()).isEqualTo("c2b-tools-v1");
        assertThat(plan.getContentVersion()).isEqualTo("2026-07-21.1");
        assertThat(plan.getRuntimeBundleHash()).isEqualTo("sha256:runtime");
        assertThat(plan.getCalls()).hasSize(3);
        assertThatThrownBy(() -> builder.build(content, intent, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tool call budget");
    }

    private void assertKinds(FollowUpIntent followUpIntent, ToolKind... expected) {
        ToolPlan plan = builder.build(content, intent(followUpIntent), 4);

        assertThat(plan.getCalls())
                .extracting(call -> call.getKind())
                .containsExactly(expected);
    }

    private QueryIntent intent(FollowUpIntent followUpIntent) {
        return new QueryIntent(
                followUpIntent,
                List.of("sql-audit"),
                List.of("claim-sql-audit-delivered"),
                AnswerSectionType.SOLUTION);
    }
}
