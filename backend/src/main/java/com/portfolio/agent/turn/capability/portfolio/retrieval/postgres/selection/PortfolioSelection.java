package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection;

import java.util.List;
import java.util.Objects;

/** 候选选择结果（不可变值对象）：状态、入选候选、整体评分与产生该结果的策略版本。 */
public final class PortfolioSelection {

    private final PortfolioSelectionStatus status;
    private final List<SelectionCandidate> candidates;
    private final SelectionScore score;
    private final String policyVersion;

    public PortfolioSelection(
            PortfolioSelectionStatus status,
            List<SelectionCandidate> candidates,
            SelectionScore score,
            String policyVersion) {
        this.status = Objects.requireNonNull(status, "status");
        this.candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        this.score = Objects.requireNonNull(score, "score");
        this.policyVersion = Objects.requireNonNull(policyVersion, "policyVersion");
    }

    public PortfolioSelectionStatus getStatus() {
        return status;
    }

    public List<SelectionCandidate> getCandidates() {
        return candidates;
    }

    public List<String> getSubjectIds() {
        return candidates.stream()
                .map(SelectionCandidate::getSubjectId)
                .toList();
    }

    public SelectionScore getScore() {
        return score;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }
}
