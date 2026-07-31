package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.ConversationGuidanceStage;
import com.portfolio.agent.answer.domain.ConversationProgress;
import com.portfolio.agent.answer.domain.ConversationTopic;
import com.portfolio.agent.answer.domain.PortfolioKnowledgeFacet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static com.portfolio.agent.answer.domain.ConversationGuidanceStage.DEEPENING;
import static com.portfolio.agent.answer.domain.ConversationGuidanceStage.OPENING;
import static com.portfolio.agent.answer.domain.ConversationGuidanceStage.WRAP_UP;
import static com.portfolio.agent.answer.domain.ConversationTopic.BACKGROUND;
import static com.portfolio.agent.answer.domain.ConversationTopic.RESPONSIBILITY;
import static com.portfolio.agent.answer.domain.ConversationTopic.SOLUTION;
import static com.portfolio.agent.answer.domain.ConversationTopic.TRADEOFF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ConversationProgressClassifierTest {

    private final ConversationProgressClassifier classifier =
            new ConversationProgressClassifier();

    @ParameterizedTest
    @MethodSource("stages")
    void selectsStageByCoveredTopicCount(
            List<ConversationTopic> prior,
            PortfolioKnowledgeFacet facet,
            ConversationGuidanceStage expected
    ) {
        ConversationProgress result = classifier.classify(
                prior,
                "继续介绍这个项目",
                facet);

        assertThat(result.getStage()).isEqualTo(expected);
    }

    static Stream<Arguments> stages() {
        return Stream.of(
                arguments(List.of(), PortfolioKnowledgeFacet.OVERVIEW, OPENING),
                arguments(
                        List.of(BACKGROUND, RESPONSIBILITY),
                        PortfolioKnowledgeFacet.IMPLEMENTATION,
                        DEEPENING),
                arguments(
                        List.of(BACKGROUND, RESPONSIBILITY, SOLUTION, TRADEOFF),
                        PortfolioKnowledgeFacet.VERIFICATION,
                        WRAP_UP));
    }

    @ParameterizedTest
    @MethodSource("explorationQuestions")
    void explicitOtherProjectRequestOverridesCoverage(String question) {
        ConversationProgress result = classifier.classify(
                List.of(BACKGROUND, SOLUTION),
                question,
                PortfolioKnowledgeFacet.IMPLEMENTATION);

        assertThat(result.getStage())
                .isEqualTo(ConversationGuidanceStage.EXPLORE_OTHERS);
        assertThat(result.getCoveredTopics())
                .containsExactly(BACKGROUND, SOLUTION);
    }

    static Stream<String> explorationQuestions() {
        return Stream.of(
                "推荐其他项目",
                "还有什么项目",
                "换个项目看看");
    }

    @Test
    void infersFacetWithoutCallingAProvider() {
        assertThat(classifier.inferFacet("你个人负责了什么"))
                .isEqualTo(PortfolioKnowledgeFacet.RESPONSIBILITY);
        assertThat(classifier.inferFacet("为什么选择这个方案，有什么取舍"))
                .isEqualTo(PortfolioKnowledgeFacet.DECISION);
        assertThat(classifier.inferFacet("故障发生后如何排查"))
                .isEqualTo(PortfolioKnowledgeFacet.INCIDENT);
        assertThat(classifier.inferFacet("最后如何验证效果"))
                .isEqualTo(PortfolioKnowledgeFacet.VERIFICATION);
        assertThat(classifier.inferFacet("项目最终结果和局限是什么"))
                .isEqualTo(PortfolioKnowledgeFacet.OUTCOME);
        assertThat(classifier.inferFacet("继续介绍"))
                .isEqualTo(PortfolioKnowledgeFacet.OVERVIEW);
    }
}
