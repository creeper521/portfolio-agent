package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.PortfolioAnswerMaterial;
import com.portfolio.agent.answer.domain.PortfolioAnswerPlan;

/** P1 composition facade for P3-grounded material. */
public interface PortfolioAnswerComposer {
    PortfolioAnswerPlan compose(PortfolioAnswerMaterial material);
}
