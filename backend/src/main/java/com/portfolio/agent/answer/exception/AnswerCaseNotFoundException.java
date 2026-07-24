package com.portfolio.agent.answer.exception;

import com.portfolio.agent.common.exception.ApplicationException;

public final class AnswerCaseNotFoundException extends ApplicationException {

    public AnswerCaseNotFoundException(String slug) {
        super(AnswerErrorCode.CASE_NOT_FOUND, "公开案例不存在: " + slug);
    }
}
