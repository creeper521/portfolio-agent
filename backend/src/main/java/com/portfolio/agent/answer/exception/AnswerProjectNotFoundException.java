package com.portfolio.agent.answer.exception;

import com.portfolio.agent.common.exception.ApplicationException;
import com.portfolio.agent.common.exception.PublicResourceErrorCode;

public final class AnswerProjectNotFoundException extends ApplicationException {

    public AnswerProjectNotFoundException(String slug) {
        super(PublicResourceErrorCode.PROJECT_NOT_FOUND, "公开项目不存在: " + slug);
    }
}
