package com.portfolio.agent.turn.planning;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import java.util.Set;

/**
 * 讨论选择模板：从推荐结果中选择某个结果项进入讨论的恢复凭据。
 *
 * <p>携带推荐上下文句柄与允许的结果项 ID 集合（1..5 个）；
 * 澄清答案必须命中集合内的结果项才能恢复。</p>
 */
public final class DiscussionSelectionTemplate implements ClarificationRecoveryTemplate {
    private final String recommendationContextHandle;
    private final Set<String> allowedResultItemIds;

    @JsonCreator
    public DiscussionSelectionTemplate(
            @JsonProperty(value = "recommendationContextHandle", required = true) String handle,
            @JsonProperty(value = "allowedResultItemIds", required = true) Set<String> itemIds) {
        if (handle == null || handle.isBlank() || handle.length() > 256) {
            throw new IllegalArgumentException("recommendation context handle is invalid");
        }
        recommendationContextHandle = handle.trim();
        allowedResultItemIds = Set.copyOf(Objects.requireNonNull(itemIds, "itemIds"));
        if (allowedResultItemIds.isEmpty() || allowedResultItemIds.size() > 5
                || allowedResultItemIds.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("allowed result items are invalid");
        }
    }
    public String getRecommendationContextHandle() { return recommendationContextHandle; }
    public Set<String> getAllowedResultItemIds() { return allowedResultItemIds; }
    /** 判断给定结果项是否在允许集合内。 */
    public boolean allows(String value) { return allowedResultItemIds.contains(value); }
}
