package com.portfolio.agent.release.benchmark.selection;

import java.util.List;

public final class MigrationIntegrityResult {
    private final boolean available;
    private final double score;
    private final boolean completeMatch;
    private final List<String> mismatches;

    public MigrationIntegrityResult(double score, boolean completeMatch, List<String> mismatches) {
        this(true, score, completeMatch, mismatches);
    }

    private MigrationIntegrityResult(
            boolean available, double score, boolean completeMatch, List<String> mismatches) {
        this.available = available;
        this.score = score;
        this.completeMatch = completeMatch;
        this.mismatches = List.copyOf(mismatches);
    }

    public static MigrationIntegrityResult unavailable() {
        return new MigrationIntegrityResult(false, Double.NaN, false, List.of("UNAVAILABLE"));
    }

    public boolean isAvailable() { return available; }
    public double getScore() { return score; }
    public boolean isCompleteMatch() { return completeMatch; }
    public List<String> getMismatches() { return mismatches; }
}
