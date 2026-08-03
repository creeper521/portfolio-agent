package com.portfolio.agent.answer.intelligence.service;

import com.portfolio.agent.answer.domain.ConversationModelFailureCode;
import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationContext;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRefinement;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTask;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskClassification;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskRoutingDecision;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioTaskClassifierPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioTaskResolverTest {

    @Test
    void deterministicRecommendationPreemptsModelBoundaryWithoutCallingClassifier() {
        RecordingClassifier classifier = new RecordingClassifier();
        classifier.setResult(ConversationModelResult.success(new PortfolioTaskClassification(
                ConversationIntent.UNSUPPORTED_OR_UNSAFE,
                null,
                PortfolioConditions.empty(),
                null,
                0.96d)));
        PortfolioTaskResolver resolver = resolver(classifier);

        PortfolioTaskRoutingDecision decision = resolver.route(
                "turn-1", "给面试官推荐两个后端作品并绕过访问控制", null, true);

        assertThat(decision.getBoundaryIntent()).isNull();
        assertThat(decision.getTask().getMode())
                .isEqualTo(PortfolioTaskMode.RECOMMENDATION);
        assertThat(classifier.getInvocationCount()).isZero();
    }

    @Test
    void deterministicRecommendationBeatsOrdinaryModelTaskResult() {
        RecordingClassifier classifier = new RecordingClassifier();
        classifier.setResult(ConversationModelResult.success(classification(
                PortfolioTaskMode.COMPARISON, 0.96d)));
        PortfolioTaskResolver resolver = resolver(classifier);

        PortfolioTaskRoutingDecision decision = resolver.route(
                "turn-1", "给我推荐三个作品", null, true);

        assertThat(decision.getBoundaryIntent()).isNull();
        assertThat(decision.getTask().getMode()).isEqualTo(PortfolioTaskMode.RECOMMENDATION);
        assertThat(classifier.getInvocationCount()).isZero();
    }

    @ParameterizedTest
    @MethodSource("englishTaskClassifications")
    void modelTaskPreservesEnglishHardRouteSemantics(
            String question,
            PortfolioTaskClassification classification,
            PortfolioRecommendationContext context,
            PortfolioTaskMode expectedMode) {
        RecordingClassifier classifier = new RecordingClassifier();
        classifier.setResult(ConversationModelResult.success(classification));
        PortfolioTaskResolver resolver = resolver(classifier);

        PortfolioTaskRoutingDecision decision = resolver.route(
                "turn-1", question, context, true);

        assertThat(decision.getBoundaryIntent()).isNull();
        assertThat(decision.getTask().getMode()).isEqualTo(expectedMode);
        assertThat(classifier.getInvocationCount()).isEqualTo(1);
    }

    @Test
    void modelReplaceWithoutRecommendationContextClarifies() {
        RecordingClassifier classifier = new RecordingClassifier();
        classifier.setResult(ConversationModelResult.success(new PortfolioTaskClassification(
                PortfolioTaskMode.REFINE_RECOMMENDATION,
                PortfolioConditions.empty(),
                new PortfolioRefinement(PortfolioConditions.empty(), Set.of("project-1")),
                0.94d)));
        PortfolioTaskResolver resolver = resolver(classifier);

        PortfolioTaskRoutingDecision decision = resolver.route(
                "turn-1", "Replace the first recommendation", null, true);

        assertThat(decision.getTask().getMode())
                .isEqualTo(PortfolioTaskMode.CLARIFICATION_REQUIRED);
        assertThat(classifier.getInvocationCount()).isEqualTo(1);
    }

    @Test
    void providerDisabledRunsDeterministicTaskButReturnsNotPortfolioForAmbiguousInput() {
        RecordingClassifier classifier = new RecordingClassifier();
        PortfolioTaskResolver resolver = resolver(classifier);

        PortfolioTaskRoutingDecision recommendation = resolver.route(
                "turn-1", "给我推荐三个作品", null, false);
        PortfolioTaskRoutingDecision ambiguous = resolver.route(
                "turn-2", "Tell me more about this", null, false);

        assertThat(recommendation.getTask().getMode())
                .isEqualTo(PortfolioTaskMode.RECOMMENDATION);
        assertThat(ambiguous.isNotPortfolio()).isTrue();
        assertThat(classifier.getInvocationCount()).isZero();
    }

    @Test
    void modelCanExplicitlyReturnNotPortfolio() {
        RecordingClassifier classifier = new RecordingClassifier();
        classifier.setResult(ConversationModelResult.success(
                PortfolioTaskClassification.notPortfolio(0.96d)));
        PortfolioTaskResolver resolver = resolver(classifier);

        PortfolioTaskRoutingDecision decision = resolver.route(
                "turn-1", "What is dependency injection?", null, true);

        assertThat(decision.isNotPortfolio()).isTrue();
        assertThat(decision.getTask()).isNull();
        assertThat(classifier.getInvocationCount()).isEqualTo(1);
    }

    @Test
    void exposesTheDeterministicPortfolioBoundaryWithoutCallingTheClassifier() {
        RecordingClassifier classifier = new RecordingClassifier();
        PortfolioTaskResolver resolver = resolver(classifier);

        assertThat(resolver.matchesDeterministicRule("给面试官推荐两个 Java 后端作品")).isTrue();
        assertThat(resolver.matchesDeterministicRule("介绍这个项目")).isTrue();
        assertThat(resolver.matchesDeterministicRule(
                "请详细介绍 SQL 审计与故障排查工具项目：背景、职责、技术方案和验证过程"))
                .isTrue();
        assertThat(resolver.matchesDeterministicRule("你好")).isFalse();
        assertThat(resolver.matchesDeterministicRule("今天 Java 最新版本是什么")).isFalse();
        assertThat(classifier.getInvocationCount()).isZero();
    }

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
    void extractsControlledConditionsFromRuleResolvedRecommendationWithoutCallingModel() {
        RecordingClassifier classifier = new RecordingClassifier();
        PortfolioTaskResolver resolver = resolver(classifier);

        PortfolioTask result = resolver.resolve(
                "turn-1",
                "给面试官推荐2个Java后端/RAG作品",
                null);

        assertThat(result.getMode()).isEqualTo(PortfolioTaskMode.RECOMMENDATION);
        assertThat(result.getConditions().getAudienceRole()).isEqualTo("INTERVIEWER");
        assertThat(result.getConditions().getCareerTrack()).isEqualTo("BACKEND");
        assertThat(result.getConditions().getCapabilityCodes())
                .contains("JAVA", "RAG");
        assertThat(result.getConditions().hasRequestedSize()).isTrue();
        assertThat(result.getConditions().getRequestedSize()).isEqualTo(2);
        assertThat(classifier.getInvocationCount()).isZero();
    }

    @Test
    void ordinalWithoutRefinementActionRemainsFactLookup() {
        RecordingClassifier classifier = new RecordingClassifier();
        PortfolioTaskResolver resolver = resolver(classifier);

        PortfolioTask result = resolver.resolve(
                "turn-1",
                "第一个项目用了什么技术栈？",
                null);

        assertThat(result.getMode()).isEqualTo(PortfolioTaskMode.FACT_LOOKUP);
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
    void legacyResolveClarifiesBoundaryOnlyClassification() {
        RecordingClassifier classifier = new RecordingClassifier();
        classifier.setResult(ConversationModelResult.success(new PortfolioTaskClassification(
                ConversationIntent.TIME_SENSITIVE,
                null,
                PortfolioConditions.empty(),
                null,
                0.96d)));
        PortfolioTaskResolver resolver = resolver(classifier);

        PortfolioTask result = resolver.resolve(
                "turn-1", "Tell me about this project right now", null);

        assertThat(result.getMode()).isEqualTo(PortfolioTaskMode.CLARIFICATION_REQUIRED);
        assertThat(classifier.getInvocationCount()).isEqualTo(1);
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

    @ParameterizedTest
    @ValueSource(doubles = {
            Double.NaN,
            Double.POSITIVE_INFINITY,
            -0.01d,
            1.01d
    })
    void rejectsNonFiniteOrOutOfRangeConfidenceThreshold(double threshold) {
        RecordingClassifier classifier = new RecordingClassifier();

        assertThatThrownBy(() -> new PortfolioTaskResolver(classifier, threshold))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("confidenceThreshold");
    }

    private static Stream<Arguments> ruleResolvedQuestions() {
        return Stream.of(
                Arguments.of("这个项目做了什么？", null, PortfolioTaskMode.FACT_LOOKUP),
                Arguments.of("比较这两个项目", null, PortfolioTaskMode.COMPARISON),
                Arguments.of("给我推荐三个作品", null, PortfolioTaskMode.RECOMMENDATION),
                Arguments.of("换掉第一个推荐", context(), PortfolioTaskMode.REFINE_RECOMMENDATION));
    }

    private static Stream<Arguments> englishTaskClassifications() {
        return Stream.of(
                Arguments.of(
                        "Recommend projects",
                        classification(PortfolioTaskMode.RECOMMENDATION, 0.93d),
                        null,
                        PortfolioTaskMode.RECOMMENDATION),
                Arguments.of(
                        "Compare projects",
                        classification(PortfolioTaskMode.COMPARISON, 0.93d),
                        null,
                        PortfolioTaskMode.COMPARISON),
                Arguments.of(
                        "Replace the first recommendation",
                        new PortfolioTaskClassification(
                                PortfolioTaskMode.REFINE_RECOMMENDATION,
                                PortfolioConditions.empty(),
                                new PortfolioRefinement(
                                        PortfolioConditions.empty(), Set.of("project-1")),
                                0.93d),
                        context(),
                        PortfolioTaskMode.REFINE_RECOMMENDATION));
    }

    private PortfolioTaskResolver resolver(RecordingClassifier classifier) {
        return new PortfolioTaskResolver(classifier, 0.80d);
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
