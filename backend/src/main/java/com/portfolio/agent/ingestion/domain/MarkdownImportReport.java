package com.portfolio.agent.ingestion.domain;

public final class MarkdownImportReport {

    private final int added;
    private final int changed;
    private final int unchanged;
    private final int missing;
    private final int failed;
    private final int blocked;
    private final int vectorPending;

    public MarkdownImportReport(
            int added, int changed, int unchanged, int missing, int failed, int blocked,
            int vectorPending) {
        requireNonNegative(added, changed, unchanged, missing, failed, blocked, vectorPending);
        this.added = added;
        this.changed = changed;
        this.unchanged = unchanged;
        this.missing = missing;
        this.failed = failed;
        this.blocked = blocked;
        this.vectorPending = vectorPending;
    }

    public int getAdded() { return added; }
    public int getChanged() { return changed; }
    public int getUnchanged() { return unchanged; }
    public int getMissing() { return missing; }
    public int getFailed() { return failed; }
    public int getBlocked() { return blocked; }
    public int getVectorPending() { return vectorPending; }
    public boolean isPartial() { return failed > 0 || blocked > 0 || vectorPending > 0; }

    private void requireNonNegative(int... values) {
        for (int value : values) {
            if (value < 0) {
                throw new IllegalArgumentException("import report counts must not be negative");
            }
        }
    }
}
