package com.portfolio.agent.turn.projection;

public record GoalNotice(String code, String message) {
    public GoalNotice {
        if (code == null || !code.matches("[A-Z][A-Z0-9_]{1,63}")) {
            throw new IllegalArgumentException("notice code is invalid");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("notice message is required");
        }
        message = message.trim();
    }
}
