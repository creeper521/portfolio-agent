package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.AnswerClaimCategory;
import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerQuestion;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationGuidanceStage;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationMessage;
import com.portfolio.agent.answer.domain.ConversationMessageRole;
import com.portfolio.agent.answer.domain.ConversationModelFailureCode;
import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.domain.ConversationProgress;
import com.portfolio.agent.answer.domain.ConversationRoute;
import com.portfolio.agent.answer.domain.ConversationSuggestedQuestion;
import com.portfolio.agent.answer.domain.ConversationWindow;
import com.portfolio.agent.answer.domain.PortfolioKnowledgeFacet;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.gateway.ConversationalModelPort;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DynamicQuestionServiceTest {

    @ParameterizedTest
    @MethodSource("stageDistributions")
    void failedModelSuggestionStillReturnsExactStageDistribution(
            ConversationGuidanceStage stage,
            int expectedCurrent,
            int expectedOther
    ) {
        ConversationalModelPort modelPort = mock(ConversationalModelPort.class);
        PortfolioGroundingAssembler assembler =
                mock(PortfolioGroundingAssembler.class);
        DynamicQuestionService service =
                new DynamicQuestionService(modelPort, assembler, 3);
        when(modelPort.suggest(any(), any(), any(), any())).thenReturn(
                ConversationModelResult.failure(
                        ConversationModelFailureCode.INVALID_RESPONSE));
        when(assembler.canAnswer(any(), any())).thenReturn(true);

        List<ConversationSuggestedQuestion> result = service.generate(
                content(),
                route(),
                new ConversationWindow(null, List.of(), 0),
                List.of(),
                new ConversationProgress(List.of(), stage),
                "请继续介绍这个项目");

        assertThat(result).hasSize(3);
        assertThat(result.stream()
                .filter(question ->
                        "sql-audit".equals(question.getProjectSlug()))
                .count()).isEqualTo(expectedCurrent);
        assertThat(result.stream()
                .filter(question ->
                        !"sql-audit".equals(question.getProjectSlug()))
                .count()).isEqualTo(expectedOther);
    }

    static Stream<Arguments> stageDistributions() {
        return Stream.of(
                Arguments.of(ConversationGuidanceStage.OPENING, 3, 0),
                Arguments.of(ConversationGuidanceStage.DEEPENING, 2, 1),
                Arguments.of(ConversationGuidanceStage.WRAP_UP, 1, 2),
                Arguments.of(ConversationGuidanceStage.EXPLORE_OTHERS, 0, 3));
    }

    @ParameterizedTest
    @MethodSource("recentQuestions")
    void excludesCurrentAndRecentQuestions(String excludedQuestion) {
        ConversationalModelPort modelPort = mock(ConversationalModelPort.class);
        PortfolioGroundingAssembler assembler =
                mock(PortfolioGroundingAssembler.class);
        DynamicQuestionService service =
                new DynamicQuestionService(modelPort, assembler, 3);
        when(modelPort.suggest(any(), any(), any(), any())).thenReturn(
                ConversationModelResult.failure(
                        ConversationModelFailureCode.INVALID_RESPONSE));
        when(assembler.canAnswer(any(), any())).thenReturn(true);
        ConversationWindow window = new ConversationWindow(
                null,
                List.of(new ConversationMessage(
                        ConversationMessageRole.USER,
                        excludedQuestion)),
                0);

        List<ConversationSuggestedQuestion> result = service.generate(
                content(),
                route(),
                window,
                List.of(),
                new ConversationProgress(
                        List.of(),
                        ConversationGuidanceStage.OPENING),
                excludedQuestion);

        assertThat(result)
                .extracting(ConversationSuggestedQuestion::getText)
                .doesNotContain(excludedQuestion);
        assertThat(result).hasSize(3);
    }

    static Stream<String> recentQuestions() {
        return Stream.of(
                "这个项目的背景和目标是什么？",
                "具体方案是如何实现的？");
    }

    private ConversationRoute route() {
        return new ConversationRoute(
                ConversationIntent.PORTFOLIO_GROUNDED,
                ConversationAnswerScope.PORTFOLIO,
                1.0,
                "sql-audit",
                null,
                PortfolioKnowledgeFacet.OVERVIEW,
                false);
    }

    private RuntimeAnswerContent content() {
        return new RuntimeAnswerContent(
                "v1",
                "hash",
                List.of(
                        project(
                                "sql-audit",
                                question(
                                        "q-sql-background",
                                        "这个项目的背景和目标是什么？",
                                        AnswerClaimCategory.BACKGROUND),
                                question(
                                        "q-sql-implementation",
                                        "具体方案是如何实现的？",
                                        AnswerClaimCategory.IMPLEMENTATION),
                                question(
                                        "q-sql-verification",
                                        "最终结果是如何验证的？",
                                        AnswerClaimCategory.VERIFICATION),
                                question(
                                        "q-sql-tradeoff",
                                        "关键技术取舍是什么？",
                                        AnswerClaimCategory.TECHNICAL_DECISION)),
                        project(
                                "codegraph",
                                question(
                                        "q-codegraph",
                                        "CodeGraph 项目解决了什么问题？",
                                        AnswerClaimCategory.BACKGROUND)),
                        project(
                                "next-ai-drawio",
                                question(
                                        "q-next-ai",
                                        "Next AI Drawio 如何验证效果？",
                                        AnswerClaimCategory.VERIFICATION)),
                        project(
                                "maven-mcp",
                                question(
                                        "q-maven",
                                        "Maven MCP 项目有哪些技术取舍？",
                                        AnswerClaimCategory.TECHNICAL_DECISION))));
    }

    private AnswerKnowledge project(
            String slug,
            AnswerQuestion... questions
    ) {
        return new AnswerKnowledge(
                slug,
                slug,
                "summary",
                "background",
                List.of(),
                "solution",
                List.of(),
                List.of(),
                "outcome",
                "handoff",
                "DELIVERED",
                List.of(questions),
                List.of(),
                List.of());
    }

    private AnswerQuestion question(
            String id,
            String text,
            AnswerClaimCategory category
    ) {
        return new AnswerQuestion(
                id,
                text,
                List.of(),
                text,
                List.of(category));
    }
}
