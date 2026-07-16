package com.portfolio.agent.answer.exception;

import com.portfolio.agent.common.exception.ErrorCode;

public enum AnswerErrorCode implements ErrorCode {

    PROJECT_NOT_FOUND("PROJECT_NOT_FOUND", "公开项目不存在", 404);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    AnswerErrorCode(String code, String defaultMessage, int httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }

    @Override
    public int getHttpStatus() {
        return httpStatus;
    }
}
