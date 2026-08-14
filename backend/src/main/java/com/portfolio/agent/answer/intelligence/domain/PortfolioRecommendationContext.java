package com.portfolio.agent.answer.intelligence.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class PortfolioRecommendationContext {

    private static final Pattern BATCH_ID_PATTERN = Pattern.compile("rec_[0-9a-f]{64}");

    private final String recommendationBatchId;
    private final String contentVersion;
    private final String careerTrack;
    private final String audienceRole;
    private final Set<String> capabilityCodes;
    private final int requestedSize;
    private final List<String> selectedPortfolioIds;

    public PortfolioRecommendationContext(
            String recommendationBatchId,
            String contentVersion,
            String careerTrack,
            String audienceRole,
            Set<String> capabilityCodes,
            int requestedSize,
            List<String> selectedPortfolioIds) {
        if (recommendationBatchId == null || !BATCH_ID_PATTERN.matcher(recommendationBatchId).matches()) {
            throw new IllegalArgumentException("recommendationBatchId format is invalid");
        }
        this.recommendationBatchId = recommendationBatchId;
        this.contentVersion = requireText(contentVersion, "contentVersion");
        this.careerTrack = normalizeControlledValue(careerTrack);
        this.audienceRole = requireControlledValue(audienceRole, "audienceRole");
        this.capabilityCodes = normalizeCapabilityCodes(capabilityCodes);
        if (requestedSize < 1 || requestedSize > 5) {
            throw new IllegalArgumentException("requestedSize must be between 1 and 5");
        }
        this.requestedSize = requestedSize;
        this.selectedPortfolioIds = normalizePortfolioIds(selectedPortfolioIds);
    }

    public String getRecommendationBatchId() { return recommendationBatchId; }
    public String getContentVersion() { return contentVersion; }
    public String getCareerTrack() { return careerTrack; }
    public String getAudienceRole() { return audienceRole; }
    public Set<String> getCapabilityCodes() { return capabilityCodes; }
    public int getRequestedSize() { return requestedSize; }
    public List<String> getSelectedPortfolioIds() { return selectedPortfolioIds; }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof PortfolioRecommendationContext that)) { return false; }
        return requestedSize == that.requestedSize
                && Objects.equals(recommendationBatchId, that.recommendationBatchId)
                && Objects.equals(contentVersion, that.contentVersion)
                && Objects.equals(careerTrack, that.careerTrack)
                && Objects.equals(audienceRole, that.audienceRole)
                && Objects.equals(capabilityCodes, that.capabilityCodes)
                && Objects.equals(selectedPortfolioIds, that.selectedPortfolioIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recommendationBatchId, contentVersion, careerTrack, audienceRole,
                capabilityCodes, requestedSize, selectedPortfolioIds);
    }

    @Override
    public String toString() {
        return "PortfolioRecommendationContext{" + "contentVersion='" + contentVersion + '\''
                + ", careerTrack='" + careerTrack + '\''
                + ", audienceRole='" + audienceRole + '\''
                + ", capabilityCount=" + capabilityCodes.size()
                + ", requestedSize=" + requestedSize
                + ", selectedPortfolioCount=" + selectedPortfolioIds.size() + '}';
    }

    private static Set<String> normalizeCapabilityCodes(Set<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values == null ? Set.<String>of() : values) {
            normalized.add(requireControlledValue(value, "capabilityCodes"));
        }
        return Set.copyOf(normalized);
    }

    private static List<String> normalizePortfolioIds(List<String> values) {
        List<String> normalized = new ArrayList<>();
        for (String value : values == null ? List.<String>of() : values) {
            normalized.add(requireText(value, "selectedPortfolioIds"));
        }
        return List.copyOf(normalized);
    }

    private static String requireControlledValue(String value, String name) {
        String normalized = normalizeControlledValue(value);
        if (normalized == null) { throw new IllegalArgumentException(name + " is required"); }
        return normalized;
    }

    private static String normalizeControlledValue(String value) {
        if (value == null || value.isBlank()) { return null; }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) { throw new IllegalArgumentException(name + " is required"); }
        return value.trim();
    }
}
