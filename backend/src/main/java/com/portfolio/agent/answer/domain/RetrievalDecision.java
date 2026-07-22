package com.portfolio.agent.answer.domain;

import java.util.List;

public final class RetrievalDecision {

    private final RetrievalDecisionType type;
    private final RetrievalMode mode;
    private final List<String> selectedChunkIds;
    private final List<String> selectedClaimIds;

    public RetrievalDecision(
            RetrievalDecisionType type,
            RetrievalMode mode,
            List<String> selectedChunkIds,
            List<String> selectedClaimIds
    ) {
        this.type = type;
        this.mode = mode;
        this.selectedChunkIds = List.copyOf(selectedChunkIds);
        this.selectedClaimIds = List.copyOf(selectedClaimIds);
    }

    public RetrievalDecisionType getType() { return type; }
    public RetrievalMode getMode() { return mode; }
    public List<String> getSelectedChunkIds() { return selectedChunkIds; }
    public List<String> getSelectedClaimIds() { return selectedClaimIds; }
}
