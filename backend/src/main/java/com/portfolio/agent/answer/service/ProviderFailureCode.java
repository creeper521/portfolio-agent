package com.portfolio.agent.answer.service;

import com.portfolio.agent.common.observability.DiagnosticCode;

public enum ProviderFailureCode implements DiagnosticCode {
    PROVIDER_TIMEOUT,
    PROVIDER_CONNECTION_FAILED,
    PROVIDER_EMPTY_RESPONSE,
    PROVIDER_INVALID_RESPONSE,
    PROVIDER_REQUEST_BUILD_FAILED,
    PROVIDER_DRAFT_REJECTED,
    PROVIDER_DISABLED;

    @Override
    public String code() {
        return name();
    }
}
