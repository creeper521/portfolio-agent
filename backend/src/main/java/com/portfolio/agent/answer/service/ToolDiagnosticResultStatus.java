package com.portfolio.agent.answer.service;

import com.portfolio.agent.answer.domain.PublicToolResultStatus;

import java.util.Objects;

public enum ToolDiagnosticResultStatus {
    SUCCESS,
    INSUFFICIENT,
    FAILURE,
    INVALID;

    public static ToolDiagnosticResultStatus fromPublicToolResultStatus(
            PublicToolResultStatus status
    ) {
        return switch (Objects.requireNonNull(status, "status")) {
            case SUCCESS -> SUCCESS;
            case INSUFFICIENT -> INSUFFICIENT;
        };
    }
}
