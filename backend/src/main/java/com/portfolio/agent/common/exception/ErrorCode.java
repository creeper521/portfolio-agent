package com.portfolio.agent.common.exception;

public interface ErrorCode {

    String getCode();

    String getDefaultMessage();

    int getHttpStatus();
}
