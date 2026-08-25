package com.portfolio.agent.turn.projection;

/**
 * 面向公众的固定提示（稳定 code + 中文文案）。
 *
 * <p>code 遵循大写常量字符集（如 COVERAGE_INCOMPLETE、OUT_OF_SCOPE），
 * 供前端按 code 做稳定分支；文案是公众可见的固定描述。</p>
 */
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
