package com.portfolio.agent.answer.domain;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToolModelContractTest {

    @Test
    void toolCallsContainOnlyClosedStableParameters() {
        assertThat(ToolKind.values()).containsExactly(
                ToolKind.GET_PROJECT,
                ToolKind.GET_CASE,
                ToolKind.GET_CLAIMS,
                ToolKind.GET_EVIDENCE_FOR_CLAIMS,
                ToolKind.GET_TIMELINE,
                ToolKind.SEARCH_PUBLIC_CONTENT,
                ToolKind.COMPARE_PROJECTS);

        assertThat(Arrays.stream(ToolCall.class.getDeclaredFields())
                .map(Field::getName))
                .containsExactly("kind", "projectSlugs", "caseSlugs", "claimIds", "sectionType")
                .doesNotContain("query", "path", "url", "command", "expression", "toolName");
    }

    @Test
    void toolModelsDefensivelyCopyStableReferences() {
        List<String> projects = new java.util.ArrayList<>(List.of("sql-audit"));
        List<String> claims = new java.util.ArrayList<>(List.of("claim-1"));
        ToolCall call = new ToolCall(
                ToolKind.GET_CLAIMS,
                projects,
                claims,
                AnswerSectionType.STATUS);
        projects.clear();
        claims.clear();

        assertThat(call.getProjectSlugs()).containsExactly("sql-audit");
        assertThat(call.getClaimIds()).containsExactly("claim-1");
    }
}
