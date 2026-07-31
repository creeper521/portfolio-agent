package com.portfolio.agent.answer.intelligence.domain;

import java.util.Objects;

public final class PortfolioRetrievalSource {

    private final String adapterId;

    public PortfolioRetrievalSource(String adapterId) {
        if (adapterId == null || adapterId.isBlank()) {
            throw new IllegalArgumentException("adapterId is required");
        }
        this.adapterId = adapterId.trim();
    }

    public String getAdapterId() { return adapterId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof PortfolioRetrievalSource that)) { return false; }
        return Objects.equals(adapterId, that.adapterId);
    }

    @Override
    public int hashCode() { return Objects.hash(adapterId); }

    @Override
    public String toString() { return "PortfolioRetrievalSource{" + "adapterId='" + adapterId + '\'' + '}'; }
}
