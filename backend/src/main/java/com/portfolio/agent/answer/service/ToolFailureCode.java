package com.portfolio.agent.answer.service;

import com.portfolio.agent.common.observability.DiagnosticCode;

public enum ToolFailureCode implements DiagnosticCode {
    TOOL_RESULT_INVALID,
    TOOL_EXECUTION_FAILED;

    @Override
    public String code() {
        return name();
    }
}
