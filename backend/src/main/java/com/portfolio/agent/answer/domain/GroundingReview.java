package com.portfolio.agent.answer.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class GroundingReview {

    private final List<Integer> unsupportedBlockIndexes;
    private final List<String> reasonCodes;

    @JsonCreator
    public GroundingReview(
            @JsonProperty("unsupportedBlockIndexes") List<Integer> unsupportedBlockIndexes,
            @JsonProperty("reasonCodes") List<String> reasonCodes
    ) {
        this.unsupportedBlockIndexes = unsupportedBlockIndexes == null
                ? List.of() : List.copyOf(unsupportedBlockIndexes);
        this.reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }

    public List<Integer> getUnsupportedBlockIndexes() {
        return unsupportedBlockIndexes;
    }

    public List<String> getReasonCodes() {
        return reasonCodes;
    }
}
