package com.portfolio.agent.selection.service;

import com.portfolio.agent.selection.domain.PortfolioSelection;
import com.portfolio.agent.selection.domain.PortfolioSelectionStatus;
import com.portfolio.agent.selection.domain.PortfolioSubjectKind;
import com.portfolio.agent.selection.domain.SelectionCandidate;
import com.portfolio.agent.selection.domain.SelectionScore;
import com.portfolio.agent.selection.domain.SelectionTarget;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ExhaustiveSelectionStrategy implements SelectionStrategy {

    private static final String POLICY_VERSION = "exhaustive-v1";

    @Override
    public PortfolioSelection select(SelectionTarget target, List<SelectionCandidate> candidates) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(candidates, "candidates");

        List<SelectionCandidate> orderedCandidates = candidates.stream()
                .sorted(Comparator.comparing(SelectionCandidate::getSubjectId))
                .toList();
        int selectionSize = Math.min(target.getRequestedSize(), orderedCandidates.size());
        if (selectionSize == 0) {
            return new PortfolioSelection(
                    PortfolioSelectionStatus.INSUFFICIENT,
                    List.of(),
                    score(target, List.of()),
                    policyVersion());
        }

        List<List<SelectionCandidate>> combinations = new ArrayList<>();
        enumerate(orderedCandidates, selectionSize, 0, new ArrayList<>(), combinations);
        List<SelectionCandidate> best = combinations.stream()
                .max((left, right) -> compareCombination(target, left, right))
                .orElseThrow();

        SelectionScore bestScore = score(target, best);
        PortfolioSelectionStatus status =
                selectionSize == target.getRequestedSize() && bestScore.getCapabilityCoverage() == 1.0
                        ? PortfolioSelectionStatus.READY
                        : PortfolioSelectionStatus.INSUFFICIENT;
        return new PortfolioSelection(status, best, bestScore, policyVersion());
    }

    @Override
    public String policyVersion() {
        return POLICY_VERSION;
    }

    private int compareCombination(
            SelectionTarget target,
            List<SelectionCandidate> left,
            List<SelectionCandidate> right) {
        double leftScore = score(target, left).getTotal();
        double rightScore = score(target, right).getTotal();
        int scoreComparison = Double.compare(leftScore, rightScore);
        if (scoreComparison != 0) {
            return scoreComparison;
        }
        return combinationKey(right).compareTo(combinationKey(left));
    }

    private void enumerate(
            List<SelectionCandidate> candidates,
            int targetSize,
            int start,
            List<SelectionCandidate> current,
            List<List<SelectionCandidate>> combinations) {
        if (current.size() == targetSize) {
            combinations.add(List.copyOf(current));
            return;
        }
        int remaining = targetSize - current.size();
        for (int index = start; index <= candidates.size() - remaining; index++) {
            current.add(candidates.get(index));
            enumerate(candidates, targetSize, index + 1, current, combinations);
            current.remove(current.size() - 1);
        }
    }

    private SelectionScore score(
            SelectionTarget target,
            List<SelectionCandidate> candidates) {
        if (candidates.isEmpty()) {
            return new SelectionScore(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }
        double targetFit = candidates.stream()
                .mapToDouble(SelectionCandidate::getTargetFit)
                .average()
                .orElse(0.0);
        double evidenceQuality = candidates.stream()
                .mapToDouble(SelectionCandidate::getEvidenceQuality)
                .average()
                .orElse(0.0);
        Set<String> coveredCapabilities = new HashSet<>();
        Map<String, Integer> capabilityCounts = new HashMap<>();
        Set<PortfolioSubjectKind> kinds = new HashSet<>();
        Set<String> careerTracks = new HashSet<>();
        double conflictPenalty = 0.0;
        for (SelectionCandidate candidate : candidates) {
            coveredCapabilities.addAll(candidate.getCapabilityCodes());
            for (String capability : candidate.getCapabilityCodes()) {
                capabilityCounts.merge(capability, 1, Integer::sum);
            }
            kinds.add(candidate.getSubjectKind());
            if (candidate.getCareerTrack() != null) {
                careerTracks.add(candidate.getCareerTrack());
            }
            conflictPenalty += candidate.getConflictPenalty();
        }

        long matchedCapabilityCount = target.getCapabilityCodes().stream()
                .filter(coveredCapabilities::contains)
                .count();
        double capabilityCoverage = target.getCapabilityCodes().isEmpty()
                ? 1.0
                : (double) matchedCapabilityCount / target.getCapabilityCodes().size();
        int totalCapabilityOccurrences = capabilityCounts.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        int duplicateCapabilityOccurrences = capabilityCounts.values().stream()
                .mapToInt(count -> Math.max(0, count - 1))
                .sum();
        double redundancyPenalty = totalCapabilityOccurrences == 0
                ? 0.0
                : (double) duplicateCapabilityOccurrences / totalCapabilityOccurrences;
        double diversity = (kinds.size() > 1 ? 0.5 : 0.0)
                + (careerTracks.size() > 1 ? 0.25 : 0.0);
        double total = targetFit * 3.0
                + capabilityCoverage * 5.0
                + evidenceQuality * 2.0
                + diversity
                - redundancyPenalty * 2.0
                - conflictPenalty;
        return new SelectionScore(
                targetFit,
                capabilityCoverage,
                evidenceQuality,
                diversity,
                redundancyPenalty,
                conflictPenalty,
                total);
    }

    private String combinationKey(List<SelectionCandidate> candidates) {
        return candidates.stream()
                .map(SelectionCandidate::getSubjectId)
                .sorted()
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
    }
}
