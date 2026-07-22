package com.portfolio.agent.answer.domain;

public final class ExecutionBudgets {

    private final long totalDeadlineMs;
    private final int maxModelAttempts;
    private final int maxToolCalls;
    private final int maxRetrievedClaims;
    private final int maxContextCharacters;

    public ExecutionBudgets(long totalDeadlineMs, int maxModelAttempts) {
        this(totalDeadlineMs, maxModelAttempts, 0, 0, 0);
    }

    public ExecutionBudgets(
            long totalDeadlineMs,
            int maxModelAttempts,
            int maxRetrievedClaims,
            int maxContextCharacters
    ) {
        this(totalDeadlineMs, maxModelAttempts, 0, maxRetrievedClaims, maxContextCharacters);
    }

    public ExecutionBudgets(
            long totalDeadlineMs,
            int maxModelAttempts,
            int maxToolCalls,
            int maxRetrievedClaims,
            int maxContextCharacters
    ) {
        if (totalDeadlineMs <= 0) {
            throw new IllegalArgumentException("totalDeadlineMs must be positive");
        }
        if (maxModelAttempts != 1) {
            throw new IllegalArgumentException("C1 maxModelAttempts must equal one");
        }
        if (maxToolCalls < 0 || maxRetrievedClaims < 0 || maxContextCharacters < 0) {
            throw new IllegalArgumentException("retrieval budgets must not be negative");
        }
        this.totalDeadlineMs = totalDeadlineMs;
        this.maxModelAttempts = maxModelAttempts;
        this.maxToolCalls = maxToolCalls;
        this.maxRetrievedClaims = maxRetrievedClaims;
        this.maxContextCharacters = maxContextCharacters;
    }

    public long getTotalDeadlineMs() { return totalDeadlineMs; }
    public int getMaxModelAttempts() { return maxModelAttempts; }
    public int getMaxToolCalls() { return maxToolCalls; }
    public int getMaxRetrievedClaims() { return maxRetrievedClaims; }
    public int getMaxContextCharacters() { return maxContextCharacters; }
}
