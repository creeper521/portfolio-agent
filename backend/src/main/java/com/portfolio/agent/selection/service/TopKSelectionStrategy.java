package com.portfolio.agent.selection.service;

import com.portfolio.agent.selection.domain.PortfolioSelection;
import com.portfolio.agent.selection.domain.PortfolioSelectionStatus;
import com.portfolio.agent.selection.domain.SelectionCandidate;
import com.portfolio.agent.selection.domain.SelectionScore;
import com.portfolio.agent.selection.domain.SelectionTarget;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class TopKSelectionStrategy implements SelectionStrategy {

    private static final String POLICY_VERSION = "top-k-v1";

    @Override
    public PortfolioSelection select(SelectionTarget target, List<SelectionCandidate> candidates) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(candidates, "candidates");
        List<SelectionCandidate> selected = candidates.stream()
                .sorted(Comparator
                        .comparingDouble(SelectionCandidate::getTargetFit)
                        .reversed()
                        .thenComparing(
                                Comparator.comparingDouble(SelectionCandidate::getEvidenceQuality)
                                        .reversed())
                        .thenComparing(SelectionCandidate::getSubjectId))
                .limit(target.getRequestedSize())
                .toList();
        Set<String> covered = new HashSet<>();
        selected.forEach(candidate -> covered.addAll(candidate.getCapabilityCodes()));
        long requestedCovered = target.getCapabilityCodes().stream().filter(covered::contains).count();
        double coverage = target.getCapabilityCodes().isEmpty()
                ? 1.0
                : (double) requestedCovered / target.getCapabilityCodes().size();
        PortfolioSelectionStatus status =
                selected.size() == target.getRequestedSize() && coverage == 1.0
                        ? PortfolioSelectionStatus.READY
                        : PortfolioSelectionStatus.INSUFFICIENT;
        double fit = selected.stream().mapToDouble(SelectionCandidate::getTargetFit).average().orElse(0.0);
        double evidence = selected.stream()
                .mapToDouble(SelectionCandidate::getEvidenceQuality)
                .average()
                .orElse(0.0);
        SelectionScore score = new SelectionScore(
                fit, coverage, evidence, 0.0, 0.0, 0.0, fit + coverage + evidence);
        return new PortfolioSelection(status, selected, score, policyVersion());
    }

    @Override
    public String policyVersion() {
        return POLICY_VERSION;
    }
}
