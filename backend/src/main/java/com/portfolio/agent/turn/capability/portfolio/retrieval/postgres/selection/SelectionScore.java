package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection;

public final class SelectionScore {

    private final double targetFit;
    private final double capabilityCoverage;
    private final double evidenceQuality;
    private final double diversity;
    private final double redundancyPenalty;
    private final double conflictPenalty;
    private final double total;

    public SelectionScore(
            double targetFit,
            double capabilityCoverage,
            double evidenceQuality,
            double diversity,
            double redundancyPenalty,
            double conflictPenalty,
            double total) {
        this.targetFit = targetFit;
        this.capabilityCoverage = capabilityCoverage;
        this.evidenceQuality = evidenceQuality;
        this.diversity = diversity;
        this.redundancyPenalty = redundancyPenalty;
        this.conflictPenalty = conflictPenalty;
        this.total = total;
    }

    public double getTargetFit() {
        return targetFit;
    }

    public double getCapabilityCoverage() {
        return capabilityCoverage;
    }

    public double getEvidenceQuality() {
        return evidenceQuality;
    }

    public double getDiversity() {
        return diversity;
    }

    public double getRedundancyPenalty() {
        return redundancyPenalty;
    }

    public double getConflictPenalty() {
        return conflictPenalty;
    }

    public double getTotal() {
        return total;
    }
}
