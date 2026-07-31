package com.portfolio.agent.answer.intelligence.gateway;

import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationContext;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskClassification;

public interface PortfolioTaskClassifierPort {

    ConversationModelResult<PortfolioTaskClassification> classifyPortfolioTask(
            String turnId,
            String question,
            PortfolioRecommendationContext recommendationContext);
}
