package com.portfolio.agent.answer.composition.gateway;
import com.portfolio.agent.answer.composition.domain.ModelExpressionDeadline;
import com.portfolio.agent.answer.composition.domain.ModelExpressionRequest;
import com.portfolio.agent.answer.composition.domain.ModelExpressionResult;
public interface PortfolioExpressionPort { ModelExpressionResult express(ModelExpressionRequest request, ModelExpressionDeadline deadline); }
