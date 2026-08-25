package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection;

/**
 * 候选选择评分（不可变值对象）：一个候选的六维评分与总分。
 *
 * <p>维度含义：targetFit 与检索目标的契合度、capabilityCoverage 对所需能力的覆盖度、
 * evidenceQuality 证据质量、diversity 多样性贡献、redundancyPenalty 冗余惩罚、
 * conflictPenalty 冲突惩罚；total 为聚合后的总分，由评分器按固定权重合成。
 */
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
