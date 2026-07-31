package com.portfolio.agent.answer.intelligence.service;

import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class RecommendationBatchFingerprint {

    private static final String PREFIX = "rec_";

    public String calculate(
            String contentVersion,
            PortfolioConditions conditions,
            List<String> selectedPortfolioIds) {
        String canonical = canonicalForm(contentVersion, conditions, selectedPortfolioIds);
        return PREFIX + toHex(sha256(canonical));
    }

    private String canonicalForm(
            String contentVersion,
            PortfolioConditions conditions,
            List<String> selectedPortfolioIds) {
        Objects.requireNonNull(conditions, "conditions");
        StringBuilder canonical = new StringBuilder();
        append(canonical, "contentVersion", requireText(contentVersion, "contentVersion"));
        append(canonical, "careerTrack", conditions.getCareerTrack());
        append(canonical, "audienceRole", conditions.getAudienceRole());
        append(canonical, "requestedSize", Integer.toString(conditions.getRequestedSize()));
        List<String> sortedCapabilities = new ArrayList<>(conditions.getCapabilityCodes());
        sortedCapabilities.sort(Comparator.naturalOrder());
        append(canonical, "capabilityCount", Integer.toString(sortedCapabilities.size()));
        for (String capability : sortedCapabilities) {
            append(canonical, "capability", capability);
        }
        List<String> selected = List.copyOf(Objects.requireNonNull(selectedPortfolioIds, "selectedPortfolioIds"));
        append(canonical, "selectedPortfolioCount", Integer.toString(selected.size()));
        for (String selectedPortfolioId : selected) {
            append(canonical, "selectedPortfolioId", requireText(selectedPortfolioId, "selectedPortfolioIds"));
        }
        return canonical.toString();
    }

    private void append(StringBuilder target, String field, String value) {
        String normalized = value == null ? "<null>" : value;
        target.append(field)
                .append('=')
                .append(normalized.length())
                .append(':')
                .append(normalized)
                .append('\n');
    }

    private byte[] sha256(String canonical) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
