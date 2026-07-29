package com.portfolio.agent.answer.exception;

import com.portfolio.agent.common.exception.ApplicationException;
import com.portfolio.agent.common.exception.PublicResourceErrorCode;

public final class AnswerCaseNotFoundException extends ApplicationException {

    public AnswerCaseNotFoundException(String slug) {
        super(PublicResourceErrorCode.CASE_NOT_FOUND, "公开案例不存在: " + slug);
    }
}
