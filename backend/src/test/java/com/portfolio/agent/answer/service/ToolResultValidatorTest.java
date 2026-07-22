package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.ExecutionBudgets;
import com.portfolio.agent.answer.domain.PublicToolResult;
import com.portfolio.agent.answer.domain.PublicToolResultStatus;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.domain.ToolCall;
import com.portfolio.agent.answer.domain.ToolKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolResultValidatorTest {

    private final ToolResultValidator validator = new ToolResultValidator();
    private final RuntimeAnswerContent content = new RuntimeAnswerContent(
            "2026-07-21.1", "sha256:runtime", List.of());
    private final ToolCall call = new ToolCall(
            ToolKind.SEARCH_PUBLIC_CONTENT, List.of("sql-audit"), List.of(), null);
    private final ExecutionBudgets budgets = new ExecutionBudgets(5000L, 1, 4, 8, 4000);

    @Test
    void rejectsToolResultFromAnotherSnapshot() {
        PublicToolResult wrongVersion = result("other-version", "sha256:runtime");
        PublicToolResult wrongBundle = result("2026-07-21.1", "sha256:other");

        assertThatThrownBy(() -> validator.validate(content, call, wrongVersion, budgets))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("snapshot");
        assertThatThrownBy(() -> validator.validate(content, call, wrongBundle, budgets))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("snapshot");
    }

    @Test
    void rejectsResultForAnotherToolKind() {
        PublicToolResult result = new PublicToolResult(
                ToolKind.GET_PROJECT,
                content.getContentVersion(),
                content.getRuntimeBundleHash(),
                PublicToolResultStatus.INSUFFICIENT,
                List.of(), List.of(), List.of(), List.of(), List.of());

        assertThatThrownBy(() -> validator.validate(content, call, result, budgets))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tool kind");
    }

    private PublicToolResult result(String contentVersion, String runtimeBundleHash) {
        return new PublicToolResult(
                ToolKind.SEARCH_PUBLIC_CONTENT,
                contentVersion,
                runtimeBundleHash,
                PublicToolResultStatus.INSUFFICIENT,
                List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
