package com.portfolio.agent.turn.capability.portfolio.presentation;

public final class PresentationPolicy {
    private final int maximumSections;
    private final int maximumCharacters;
    public PresentationPolicy(int maximumSections, int maximumCharacters) {
        if (maximumSections < 1 || maximumSections > 16
                || maximumCharacters < 200 || maximumCharacters > 20000) {
            throw new IllegalArgumentException("presentation bounds are invalid");
        }
        this.maximumSections = maximumSections;
        this.maximumCharacters = maximumCharacters;
    }
    public static PresentationPolicy defaults() { return new PresentationPolicy(8, 12000); }
    public int getMaximumSections() { return maximumSections; }
    public int getMaximumCharacters() { return maximumCharacters; }
}
