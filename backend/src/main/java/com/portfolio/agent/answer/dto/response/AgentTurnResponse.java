package com.portfolio.agent.answer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

/** Response-only shell retained until the Slice-5 atomic public contract cutover. */
public final class AgentTurnResponse {
    private final String contractVersion;
    private final AgentInteractionResponse interaction;

    public AgentTurnResponse(String contractVersion, AgentInteractionResponse interaction) {
        if (contractVersion == null || contractVersion.isBlank()) {
            throw new IllegalArgumentException("contractVersion is required");
        }
        this.contractVersion = contractVersion.trim();
        this.interaction = Objects.requireNonNull(interaction, "interaction");
    }

    public String getContractVersion() { return contractVersion; }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public AgentInteractionResponse getInteraction() { return interaction; }
}
