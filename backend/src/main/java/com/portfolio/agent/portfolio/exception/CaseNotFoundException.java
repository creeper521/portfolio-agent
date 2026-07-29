package com.portfolio.agent.portfolio.exception;

import com.portfolio.agent.common.exception.ApplicationException;
import com.portfolio.agent.common.exception.PublicResourceErrorCode;

public final class CaseNotFoundException extends ApplicationException {

    public CaseNotFoundException(String slug) {
        super(PublicResourceErrorCode.CASE_NOT_FOUND, "公开案例不存在: " + slug);
    }
}
