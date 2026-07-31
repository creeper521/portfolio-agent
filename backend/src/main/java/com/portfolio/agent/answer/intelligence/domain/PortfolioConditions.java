package com.portfolio.agent.answer.intelligence.domain;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class PortfolioConditions {

    private static final int DEFAULT_REQUESTED_SIZE = 3;

    private final String careerTrack;
    private final String audienceRole;
    private final Set<String> capabilityCodes;
    private final String goal;
    private final int requestedSize;

    public PortfolioConditions(
            String careerTrack,
            String audienceRole,
            Set<String> capabilityCodes,
            String goal,
            int requestedSize) {
        if (requestedSize < 2 || requestedSize > 5) {
            throw new IllegalArgumentException("requestedSize must be between 2 and 5");
        }
        this.careerTrack = normalizeControlledValue(careerTrack);
        this.audienceRole = normalizeControlledValue(audienceRole);
        this.capabilityCodes = normalizeCapabilityCodes(capabilityCodes);
        this.goal = normalizeText(goal);
        this.requestedSize = requestedSize;
    }

    public static PortfolioConditions empty() {
        return new PortfolioConditions(null, null, Set.of(), null, DEFAULT_REQUESTED_SIZE);
    }

    public PortfolioConditions merge(PortfolioConditions overrides) {
        Objects.requireNonNull(overrides, "overrides");
        Set<String> mergedCapabilityCodes = new LinkedHashSet<>(capabilityCodes);
        mergedCapabilityCodes.addAll(overrides.capabilityCodes);
        return new PortfolioConditions(
                firstPresent(overrides.careerTrack, careerTrack),
                firstPresent(overrides.audienceRole, audienceRole),
                mergedCapabilityCodes,
                firstPresent(overrides.goal, goal),
                overrides.requestedSize);
    }

    public String getCareerTrack() { return careerTrack; }
    public String getAudienceRole() { return audienceRole; }
    public Set<String> getCapabilityCodes() { return capabilityCodes; }
    public String getGoal() { return goal; }
    public int getRequestedSize() { return requestedSize; }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof PortfolioConditions that)) { return false; }
        return requestedSize == that.requestedSize
                && Objects.equals(careerTrack, that.careerTrack)
                && Objects.equals(audienceRole, that.audienceRole)
                && Objects.equals(capabilityCodes, that.capabilityCodes)
                && Objects.equals(goal, that.goal);
    }

    @Override
    public int hashCode() {
        return Objects.hash(careerTrack, audienceRole, capabilityCodes, goal, requestedSize);
    }

    @Override
    public String toString() {
        return "PortfolioConditions{" + "careerTrack='" + careerTrack + '\''
                + ", audienceRole='" + audienceRole + '\''
                + ", capabilityCount=" + capabilityCodes.size()
                + ", goal='<redacted>'"
                + ", requestedSize=" + requestedSize + '}';
    }

    private static String firstPresent(String primary, String fallback) {
        return primary == null ? fallback : primary;
    }

    private static Set<String> normalizeCapabilityCodes(Set<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values == null ? Set.<String>of() : values) {
            String normalizedValue = normalizeControlledValue(value);
            if (normalizedValue == null) {
                throw new IllegalArgumentException("capabilityCodes must not contain blank values");
            }
            normalized.add(normalizedValue);
        }
        return Set.copyOf(normalized);
    }

    private static String normalizeControlledValue(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String normalizeText(String value) {
        if (value == null || value.isBlank()) { return null; }
        return value.trim();
    }
}
