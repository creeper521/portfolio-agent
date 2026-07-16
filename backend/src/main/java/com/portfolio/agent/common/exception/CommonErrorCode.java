package com.portfolio.agent.common.exception;

public enum CommonErrorCode implements ErrorCode {

    VALIDATION_ERROR("VALIDATION_ERROR", "请求参数不符合要求", 400),
    NOT_FOUND("NOT_FOUND", "请求的资源不存在", 404),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", "请求方法不受支持", 405),
    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", "请求内容类型不受支持", 415),
    INTERNAL_ERROR("INTERNAL_ERROR", "服务暂时不可用，请稍后重试", 500);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    CommonErrorCode(String code, String defaultMessage, int httpStatus) {
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
