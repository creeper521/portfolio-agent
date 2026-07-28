package com.portfolio.agent.answer.exception;

import com.portfolio.agent.common.exception.ErrorCode;

public enum AnswerErrorCode implements ErrorCode {

    INVALID_ANSWER_CONTEXT("INVALID_ANSWER_CONTEXT", "回答上下文包含无效的公开证据引用", 400),

    PROJECT_NOT_FOUND("PROJECT_NOT_FOUND", "公开项目不存在", 404),

    CASE_NOT_FOUND("CASE_NOT_FOUND", "公开案例不存在", 404),
    ANSWER_RATE_LIMITED("ANSWER_RATE_LIMITED", "请求过于频繁，请稍后再试。", 429),
    ANSWER_CONCURRENCY_LIMITED("ANSWER_CONCURRENCY_LIMITED", "当前请求较多，请稍后再试。", 429),
    ANSWER_REQUEST_TIMEOUT("ANSWER_REQUEST_TIMEOUT", "回答处理超时，请稍后重试。", 503);

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
