package com.portfolio.agent.answer.intelligence.service;

import com.portfolio.agent.answer.adapter.model.ConversationalAgentProperties;
import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationContext;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRefinement;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTask;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskClassification;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import com.portfolio.agent.answer.intelligence.gateway.PortfolioTaskClassifierPort;

import java.util.Locale;
import java.util.Objects;

public final class PortfolioTaskResolver {

    private final PortfolioTaskClassifierPort classifier;
    private final ConversationalAgentProperties properties;

    public PortfolioTaskResolver(
            PortfolioTaskClassifierPort classifier,
            ConversationalAgentProperties properties) {
        this.classifier = Objects.requireNonNull(classifier, "classifier");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public PortfolioTask resolve(
            String turnId,
            String question,
            PortfolioRecommendationContext recommendationContext) {
        PortfolioTaskMode ruleMode = resolveRule(question);
        if (ruleMode != null) {
            if (ruleMode == PortfolioTaskMode.REFINE_RECOMMENDATION
                    && recommendationContext == null) {
                return clarification(turnId, question, recommendationContext);
            }
            return task(turnId, question, ruleMode, 1.0d, PortfolioConditions.empty(),
                    recommendationContext, null);
        }
        return resolveWithClassifier(turnId, question, recommendationContext);
    }

    private PortfolioTask resolveWithClassifier(
            String turnId,
            String question,
            PortfolioRecommendationContext recommendationContext) {
        ConversationModelResult<PortfolioTaskClassification> result =
                classifier.classifyPortfolioTask(turnId, question, recommendationContext);
        if (!result.isSuccessful()) {
            return clarification(turnId, question, recommendationContext);
        }
        PortfolioTaskClassification classification = result.getValue();
        if (classification.getConfidence() < properties.getMinimumPortfolioTaskConfidence()) {
            return clarification(turnId, question, recommendationContext);
        }
        if (classification.getMode() == PortfolioTaskMode.REFINE_RECOMMENDATION
                && recommendationContext == null) {
            return clarification(turnId, question, recommendationContext);
        }
        return task(
                turnId,
                question,
                classification.getMode(),
                classification.getConfidence(),
                classification.getConditions(),
                recommendationContext,
                classification.getRefinement());
    }

    private PortfolioTaskMode resolveRule(String question) {
        String normalized = Objects.requireNonNull(question, "question").toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "换掉", "替换", "调整推荐", "再偏", "第一个", "第二个", "数量改")) {
            return PortfolioTaskMode.REFINE_RECOMMENDATION;
        }
        if (containsAny(normalized, "比较", "对比", "区别", "哪个好", "哪一个更")) {
            return PortfolioTaskMode.COMPARISON;
        }
        if (containsAny(normalized, "推荐", "适合我的作品", "适合我展示")) {
            return PortfolioTaskMode.RECOMMENDATION;
        }
        if (containsAny(normalized, "做了什么", "做过什么", "介绍这个项目", "介绍这个案例", "技术栈", "怎么实现")) {
            return PortfolioTaskMode.FACT_LOOKUP;
        }
        return null;
    }

    private boolean containsAny(String question, String... phrases) {
        for (String phrase : phrases) {
            if (question.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private PortfolioTask clarification(
            String turnId,
            String question,
            PortfolioRecommendationContext recommendationContext) {
        return task(
                turnId,
                question,
                PortfolioTaskMode.CLARIFICATION_REQUIRED,
                1.0d,
                PortfolioConditions.empty(),
                recommendationContext,
                null);
    }

    private PortfolioTask task(
            String turnId,
            String question,
            PortfolioTaskMode mode,
            double confidence,
            PortfolioConditions conditions,
            PortfolioRecommendationContext recommendationContext,
            PortfolioRefinement refinement) {
        return new PortfolioTask(
                turnId,
                question,
                mode,
                confidence,
                conditions,
                recommendationContext,
                refinement);
    }
}
