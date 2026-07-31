package com.portfolio.agent.answer.intelligence.domain;

import java.util.List;
import java.util.Objects;

public final class PortfolioRetrievalResult {

    private final String contentVersion;
    private final List<PortfolioRecommendationItem> items;
    private final boolean degraded;
    private final String noticeCode;

    public PortfolioRetrievalResult(
            String contentVersion,
            List<PortfolioRecommendationItem> items,
            boolean degraded,
            String noticeCode) {
        if (contentVersion == null || contentVersion.isBlank()) {
            throw new IllegalArgumentException("contentVersion is required");
        }
        this.contentVersion = contentVersion.trim();
        this.items = List.copyOf(Objects.requireNonNull(items, "items"));
        this.degraded = degraded;
        this.noticeCode = normalizeNullable(noticeCode);
    }

    public String getContentVersion() { return contentVersion; }
    public List<PortfolioRecommendationItem> getItems() { return items; }
    public boolean isDegraded() { return degraded; }
    public String getNoticeCode() { return noticeCode; }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof PortfolioRetrievalResult that)) { return false; }
        return degraded == that.degraded
                && Objects.equals(contentVersion, that.contentVersion)
                && Objects.equals(items, that.items)
                && Objects.equals(noticeCode, that.noticeCode);
    }

    @Override
    public int hashCode() { return Objects.hash(contentVersion, items, degraded, noticeCode); }

    @Override
    public String toString() {
        return "PortfolioRetrievalResult{" + "contentVersion='" + contentVersion + '\''
                + ", itemCount=" + items.size() + ", degraded=" + degraded
                + ", noticeCode='" + noticeCode + '\'' + '}';
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
