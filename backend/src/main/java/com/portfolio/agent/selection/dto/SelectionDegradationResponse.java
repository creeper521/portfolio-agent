package com.portfolio.agent.selection.dto;

public final class SelectionDegradationResponse {

    private final String code;
    private final String message;

    public SelectionDegradationResponse(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
