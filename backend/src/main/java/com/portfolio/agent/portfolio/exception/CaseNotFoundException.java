package com.portfolio.agent.portfolio.exception;

import com.portfolio.agent.common.exception.ApplicationException;

public final class CaseNotFoundException extends ApplicationException {

    public CaseNotFoundException(String slug) {
        super(PortfolioErrorCode.CASE_NOT_FOUND, "公开案例不存在: " + slug);
    }
}
