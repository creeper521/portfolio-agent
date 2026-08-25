package com.portfolio.agent.turn.capability.portfolio.presentation;

/** 呈现预算策略（不可变值对象）：限制公开回答的段落数与字符数，防止超出发布契约的输出体量。 */
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
