package com.portfolio.agent.portfolio.dto.response;

import java.util.Objects;

/**
 * 公开内容中的中性 Agent 可用性投影。
 *
 * 它只告诉前端当前部署是否接受 Turn，不暴露 State 存储类型，
 * 也不依赖 Agent Turn 模块。
 */
public final class AgentAvailabilityResponse {
    public enum Status { AVAILABLE, UNAVAILABLE }

    private final Status status;

    public AgentAvailabilityResponse(Status status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    public Status getStatus() {
        return status;
    }

    public static AgentAvailabilityResponse available() {
        return new AgentAvailabilityResponse(Status.AVAILABLE);
    }

    public static AgentAvailabilityResponse unavailable() {
        return new AgentAvailabilityResponse(Status.UNAVAILABLE);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof AgentAvailabilityResponse that
                && status == that.status;
    }

    @Override
    public int hashCode() {
        return status.hashCode();
    }
}
