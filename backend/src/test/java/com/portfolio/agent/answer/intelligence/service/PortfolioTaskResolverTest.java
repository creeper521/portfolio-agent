package com.portfolio.agent.answer.intelligence.service;

import com.portfolio.agent.answer.adapter.model.ConversationalAgentProperties;
import com.portfolio.agent.answer.domain.ConversationModelFailureCode;
import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationContext;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRefinement;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTask;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskClassification;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioTaskClassifierPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioTaskResolverTest {

    @ParameterizedTest
    @MethodSource("ruleResolvedQuestions")
    void resolvesUnambiguousRulesWithoutCallingTheModel(
            String question,
            PortfolioRecommendationContext context,
            PortfolioTaskMode expectedMode
    ) {
        RecordingClassifier classifier = new RecordingClassifier();
        PortfolioTaskResolver resolver = resolver(classifier);

        PortfolioTask result = resolver.resolve("turn-1", question, context);

        assertThat(result.getMode()).isEqualTo(expectedMode);
        assertThat(result.getConfidence()).isEqualTo(1.0d);
        assertThat(classifier.getInvocationCount()).isZero();
    }

    @Test
    void asksForClarificationWhenRecommendationRefinementHasNoContext() {
        RecordingClassifier classifier = new RecordingClassifier();
        PortfolioTaskResolver resolver = resolver(classifier);

        PortfolioTask result = resolver.resolve("turn-1", "换掉第一个推荐", null);

        assertThat(result.getMode()).isEqualTo(PortfolioTaskMode.CLARIFICATION_REQUIRED);
        assertThat(classifier.getInvocationCount()).isZero();
    }

    @Test
    void callsClassifierOnlyForAmbiguousQuestionAndPreservesUnfilledRequestedSize() {
        RecordingClassifier classifier = new RecordingClassifier();
        classifier.setResult(ConversationModelResult.success(new PortfolioTaskClassification(
                PortfolioTaskMode.RECOMMENDATION,
                new PortfolioConditions("backend", "interviewer", Set.of("rag"), "当前目标", null),
                null,
                0.91d)));
        PortfolioTaskResolver resolver = resolver(classifier);

        PortfolioTask result = resolver.resolve("turn-1", "请根据我的情况处理", null);

        assertThat(result.getMode()).isEqualTo(PortfolioTaskMode.RECOMMENDATION);
        assertThat(result.getConditions().hasRequestedSize()).isFalse();
        assertThat(result.getConditions().getRequestedSize()).isEqualTo(3);
        assertThat(classifier.getInvocationCount()).isEqualTo(1);
    }

    @Test
    void asksForClarificationWhenClassifierConfidenceIsBelowConfiguredThreshold() {
        RecordingClassifier classifier = new RecordingClassifier();
        classifier.setResult(ConversationModelResult.success(classification(
                PortfolioTaskMode.FACT_LOOKUP, 0.79d)));
        PortfolioTaskResolver resolver = resolver(classifier);

        PortfolioTask result = resolver.resolve("turn-1", "请根据我的情况处理", null);

        assertThat(result.getMode()).isEqualTo(PortfolioTaskMode.CLARIFICATION_REQUIRED);
        assertThat(classifier.getInvocationCount()).isEqualTo(1);
    }

    @Test
    void asksForClarificationWhenClassifierFailsOrTimesOut() {
        RecordingClassifier classifier = new RecordingClassifier();
        classifier.setResult(ConversationModelResult.failure(ConversationModelFailureCode.TIMEOUT));
        PortfolioTaskResolver resolver = resolver(classifier);

        PortfolioTask result = resolver.resolve("turn-1", "请根据我的情况处理", null);

        assertThat(result.getMode()).isEqualTo(PortfolioTaskMode.CLARIFICATION_REQUIRED);
    }

    @Test
    void rejectsModelRefinementWhenCompleteRecommendationContextIsAbsent() {
        RecordingClassifier classifier = new RecordingClassifier();
        classifier.setResult(ConversationModelResult.success(new PortfolioTaskClassification(
                PortfolioTaskMode.REFINE_RECOMMENDATION,
                PortfolioConditions.empty(),
                new PortfolioRefinement(PortfolioConditions.empty(), Set.of("project-1")),
                0.92d)));
        PortfolioTaskResolver resolver = resolver(classifier);

        PortfolioTask result = resolver.resolve("turn-1", "请根据我的情况处理", null);

        assertThat(result.getMode()).isEqualTo(PortfolioTaskMode.CLARIFICATION_REQUIRED);
        assertThat(result.getRecommendationContext()).isNull();
    }

    private static Stream<Arguments> ruleResolvedQuestions() {
        return Stream.of(
                Arguments.of("这个项目做了什么？", null, PortfolioTaskMode.FACT_LOOKUP),
                Arguments.of("比较这两个项目", null, PortfolioTaskMode.COMPARISON),
                Arguments.of("给我推荐三个作品", null, PortfolioTaskMode.RECOMMENDATION),
                Arguments.of("换掉第一个推荐", context(), PortfolioTaskMode.REFINE_RECOMMENDATION));
    }

    private PortfolioTaskResolver resolver(RecordingClassifier classifier) {
        ConversationalAgentProperties properties = new ConversationalAgentProperties();
        return new PortfolioTaskResolver(classifier, properties);
    }

    private static PortfolioTaskClassification classification(PortfolioTaskMode mode, double confidence) {
        return new PortfolioTaskClassification(mode, PortfolioConditions.empty(), null, confidence);
    }

    private static PortfolioRecommendationContext context() {
        return new PortfolioRecommendationContext(
                "rec_" + "a".repeat(64),
                "public-2026-07-31",
                "backend",
                "interviewer",
                Set.of("rag"),
                3,
                List.of("project-1", "case-2", "case-3"));
    }

    private static final class RecordingClassifier implements PortfolioTaskClassifierPort {
        private ConversationModelResult<PortfolioTaskClassification> result =
                ConversationModelResult.failure(ConversationModelFailureCode.PROVIDER_ERROR);
        private int invocationCount;

        @Override
        public ConversationModelResult<PortfolioTaskClassification> classifyPortfolioTask(
                String turnId,
                String question,
                PortfolioRecommendationContext recommendationContext) {
            invocationCount++;
            return result;
        }

        private void setResult(ConversationModelResult<PortfolioTaskClassification> result) {
            this.result = result;
        }

        private int getInvocationCount() {
            return invocationCount;
        }
    }
}
