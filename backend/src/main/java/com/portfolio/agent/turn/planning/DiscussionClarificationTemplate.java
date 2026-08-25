package com.portfolio.agent.turn.planning;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Set;

/**
 * Typed recovery authority for an ambiguous request inside project discussion.
 *
 * <p>讨论澄清模板：项目讨论内请求含义不明时的恢复凭据。携带讨论上下文
 * 句柄、项目 ID、允许继续的侧面集合与是否允许重入；选择动作必须落在
 * 这些闭合许可内才能恢复。</p>
 */
public final class DiscussionClarificationTemplate
        implements ClarificationRecoveryTemplate {
    private final String contextHandle;
    private final String projectId;
    private final Set<UserGoalProposal.Facet> allowedFacets;
    private final boolean reenterAllowed;

    @JsonCreator
    public DiscussionClarificationTemplate(
            @JsonProperty(value = "contextHandle", required = true) String contextHandle,
            @JsonProperty(value = "projectId", required = true) String projectId,
            @JsonProperty(value = "allowedFacets", required = true)
            Set<UserGoalProposal.Facet> allowedFacets,
            @JsonProperty(value = "reenterAllowed", required = true) boolean reenterAllowed) {
        this.contextHandle = bounded(contextHandle, "contextHandle", 256);
        this.projectId = bounded(projectId, "projectId", 128);
        this.allowedFacets = Set.copyOf(Objects.requireNonNull(
                allowedFacets, "allowedFacets"));
        this.reenterAllowed = reenterAllowed;
        if (this.allowedFacets.stream().anyMatch(facet -> facet == null)
                || this.allowedFacets.isEmpty() == !reenterAllowed) {
            throw new IllegalArgumentException(
                    "discussion clarification actions are invalid");
        }
    }

    public String getContextHandle() { return contextHandle; }
    public String getProjectId() { return projectId; }
    public Set<UserGoalProposal.Facet> getAllowedFacets() { return allowedFacets; }
    public boolean isReenterAllowed() { return reenterAllowed; }
    /** 判断是否允许继续指定侧面的讨论。 */
    public boolean allowsFacet(UserGoalProposal.Facet facet) {
        return allowedFacets.contains(facet);
    }

    private static String bounded(String value, String name, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.trim();
    }
}
