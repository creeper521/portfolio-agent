package com.portfolio.agent.turn.planning;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import java.util.Set;

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
    public boolean allows(String value) { return allowedResultItemIds.contains(value); }
}
