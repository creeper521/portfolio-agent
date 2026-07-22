package com.portfolio.agent.answer.gateway;

import com.portfolio.agent.answer.domain.ModelExpressionRequest;
import com.portfolio.agent.answer.domain.ModelExpressionResult;

public interface ModelExpressionPort {

    ModelExpressionResult express(ModelExpressionRequest request);
}
