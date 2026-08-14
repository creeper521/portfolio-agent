package com.portfolio.agent.answer.adapter.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationalPromptFactoryTest {

    private final ConversationalPromptFactory factory =
            new ConversationalPromptFactory(new ObjectMapper(), "base");

    @Test
    void generationPromptDeclaresExactConversationDraftShape() {
        String prompt = factory.systemPrompt("generation");

        assertThat(prompt)
                .contains("title", "resolution", "blocks")
                .contains("sourceScope", "content", "claimIds", "evidenceIds")
                .contains("ANSWERED", "BOUNDARY", "REJECTED")
                .contains("不要输出未声明字段");
    }

    @ParameterizedTest
    @MethodSource("operationContracts")
    void declaresContractForEveryOperation(
            String operation,
            List<String> requiredFragments
    ) {
        assertThat(factory.systemPrompt(operation))
                .contains(requiredFragments.toArray(String[]::new));
    }

    private static Stream<Arguments> operationContracts() {
        return Stream.of(
                Arguments.of("intent", List.of(
                        "intent", "answerScope", "confidence", "projectSlug",
                        "caseSlug", "facet", "clarificationRequired")),
                Arguments.of("portfolio_task", List.of(
                        "boundaryIntent", "TIME_SENSITIVE", "UNSUPPORTED_OR_UNSAFE",
                        "mode", "FACT_LOOKUP", "COMPARISON", "RECOMMENDATION",
                        "REFINE_RECOMMENDATION", "CLARIFICATION_REQUIRED", "conditions",
                        "refinement", "confidence", "requestedSize")),
                Arguments.of("tool_plan", List.of(
                        "calls", "kind", "projectSlugs", "caseSlugs",
                        "claimIds", "sectionType")),
                Arguments.of("generation", List.of(
                        "title", "resolution", "blocks", "sourceScope",
                        "content", "claimIds", "evidenceIds")),
                Arguments.of("semantic_route", List.of(
                        "taskCandidates", "dependencyCandidates", "exclusionCandidates")),
                Arguments.of("general_answer_material", List.of(
                        "general-material-v1", "DEFINITION", "MECHANISM",
                        "GENERAL_KNOWLEDGE", "contentVersion", "audienceRole")),
                Arguments.of("cross_domain_expression", List.of(
                        "cross-domain-expression-v1", "relationAlias", "statementAliases")),
                Arguments.of("review", List.of(
                        "unsupportedBlockIndexes", "reasonCodes")),
                Arguments.of("suggestion", List.of(
                        "questions", "text", "projectSlug", "caseSlug", "facet")),
                Arguments.of("summary", List.of("summary"))
        );
    }
}
