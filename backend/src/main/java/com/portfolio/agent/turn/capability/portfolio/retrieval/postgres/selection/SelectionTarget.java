package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import java.util.Set;

/**
 * 候选选择目标（不可变值对象，可 JSON 反序列化）：描述一次选择面向的受众与规模。
 *
 * <p>requestedSize 限制在 1..5（选择规模上限）；audienceRole 必填，
 * careerTrack 与 goal 可空（空白归一化为 null）。
 */
public final class SelectionTarget {

    private final String careerTrack;
    private final String audienceRole;
    private final Set<String> capabilityCodes;
    private final String goal;
    private final int requestedSize;

    public SelectionTarget(
            String careerTrack,
            String audienceRole,
            Set<String> capabilityCodes,
            int requestedSize) {
        this(careerTrack, audienceRole, capabilityCodes, null, requestedSize);
    }

    @JsonCreator
    public SelectionTarget(
            @JsonProperty("careerTrack") String careerTrack,
            @JsonProperty("audienceRole") String audienceRole,
            @JsonProperty("capabilityCodes") Set<String> capabilityCodes,
            @JsonProperty("goal") String goal,
            @JsonProperty("requestedSize") int requestedSize) {
        if (requestedSize < 1 || requestedSize > 5) {
            throw new IllegalArgumentException("requestedSize must be between 1 and 5");
        }
        this.careerTrack = normalizeNullable(careerTrack);
        this.audienceRole = requireText(audienceRole, "audienceRole");
        this.capabilityCodes = Set.copyOf(Objects.requireNonNull(capabilityCodes, "capabilityCodes"));
        this.goal = normalizeNullable(goal);
        this.requestedSize = requestedSize;
    }

    public String getCareerTrack() {
        return careerTrack;
    }

    public String getAudienceRole() {
        return audienceRole;
    }

    public Set<String> getCapabilityCodes() {
        return capabilityCodes;
    }

    public String getGoal() {
        return goal;
    }

    public int getRequestedSize() {
        return requestedSize;
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
