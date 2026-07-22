package com.portfolio.agent.answer.exception;

import com.portfolio.agent.common.exception.ApplicationException;

public final class AnswerProjectNotFoundException extends ApplicationException {

    public AnswerProjectNotFoundException(String slug) {
        super(AnswerErrorCode.PROJECT_NOT_FOUND, "公开项目不存在: " + slug);
    }
}