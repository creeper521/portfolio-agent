package com.portfolio.agent.answer.intelligence.service;

import com.portfolio.agent.answer.intelligence.domain.PortfolioClarification;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTask;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import java.util.Objects;

public final class PortfolioTaskValidator {

    public PortfolioTaskValidation validate(PortfolioTask task) {
        Objects.requireNonNull(task, "task");
        return switch (task.getMode()) {
            case FACT_LOOKUP, COMPARISON -> PortfolioTaskValidation.valid();
            case RECOMMENDATION -> recommendationValidation(task);
            case REFINE_RECOMMENDATION -> refinementValidation(task);
            case CLARIFICATION_REQUIRED -> clarification("intent", "请说明希望查询、比较或推荐的作品集内容。");
        };
    }

    private PortfolioTaskValidation recommendationValidation(PortfolioTask task) {
        if (task.getConditions().getAudienceRole() == null) {
            return clarification("audienceRole", "请说明推荐将面向哪类受众。");
        }
        if (task.getRecommendationContext() != null) {
            return clarification("recommendationContext", "请基于当前推荐结果继续调整。");
        }
        if (task.getRefinement() != null) {
            return clarification("refinement", "请说明需要调整的推荐条件。");
        }
        return PortfolioTaskValidation.valid();
    }

    private PortfolioTaskValidation refinementValidation(PortfolioTask task) {
        if (task.getRecommendationContext() == null) {
            return clarification("recommendationContext", "请基于当前推荐结果继续调整。");
        }
        if (task.getRefinement() == null) {
            return clarification("refinement", "请说明需要如何调整当前推荐。");
        }
        return PortfolioTaskValidation.valid();
    }

    private PortfolioTaskValidation clarification(String missingCondition, String question) {
        return PortfolioTaskValidation.clarification(
                new PortfolioClarification(question, missingCondition));
    }
}
