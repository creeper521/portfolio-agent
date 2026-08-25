package com.portfolio.agent.infrastructure.model;

import com.portfolio.agent.turn.execution.TurnDeadline;

import java.util.Objects;

/**
 * 一次结构化模型调用请求：Operation 名、双段提示词、输出预算、温度与截止时间。
 *
 * <p>紧凑构造器执行 fail-closed 校验：三个文本字段必须非空白，
 * maxOutputTokens 必须落在 1..8000，temperature 必须是 0..1 的有限值，
 * deadline 不允许为 null；任一不满足直接抛出 IllegalArgumentException，
 * 保证进入传输层的请求都已满足 Operation 预算约束。
 */
public record StructuredModelRequest(
        String operation, String systemPrompt, String userPrompt,
        int maxOutputTokens, double temperature, TurnDeadline deadline) {
    public StructuredModelRequest {
        operation = text(operation, "operation");
        systemPrompt = text(systemPrompt, "systemPrompt");
        userPrompt = text(userPrompt, "userPrompt");
        if (maxOutputTokens < 1 || maxOutputTokens > 8000
                || !Double.isFinite(temperature) || temperature < 0 || temperature > 1) {
            throw new IllegalArgumentException("model bounds are invalid");
        }
        Objects.requireNonNull(deadline, "deadline");
    }
    private static String text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
