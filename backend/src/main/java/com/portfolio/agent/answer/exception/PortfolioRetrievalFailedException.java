package com.portfolio.agent.answer.exception;

import com.portfolio.agent.answer.intelligence.gateway.PortfolioRetrievalException;
import com.portfolio.agent.common.exception.ApplicationException;

public final class PortfolioRetrievalFailedException extends ApplicationException {

    public PortfolioRetrievalFailedException(PortfolioRetrievalException cause) {
        super(
                AnswerErrorCode.PORTFOLIO_RETRIEVAL_FAILED,
                AnswerErrorCode.PORTFOLIO_RETRIEVAL_FAILED.getDefaultMessage());
        initCause(cause);
    }
}
