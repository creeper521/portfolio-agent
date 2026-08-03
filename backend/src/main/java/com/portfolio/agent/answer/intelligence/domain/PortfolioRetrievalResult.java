package com.portfolio.agent.answer.intelligence.domain;

import java.util.List;
import java.util.Objects;

public final class PortfolioRetrievalResult {

    private final String contentVersion;
    private final List<PortfolioRetrievedSubject> subjects;
    private final List<PortfolioRetrievedPassage> passages;
    private final PortfolioRetrievalSource source;
    private final boolean degraded;
    private final String noticeCode;

    public PortfolioRetrievalResult(
            String contentVersion,
            List<PortfolioRetrievedSubject> subjects,
            List<PortfolioRetrievedPassage> passages,
            PortfolioRetrievalSource source,
            boolean degraded,
            String noticeCode) {
        if (contentVersion == null || contentVersion.isBlank()) {
            throw new IllegalArgumentException("contentVersion is required");
        }
        this.contentVersion = contentVersion.trim();
        this.subjects = List.copyOf(Objects.requireNonNull(subjects, "subjects"));
        this.passages = List.copyOf(Objects.requireNonNull(passages, "passages"));
        this.source = Objects.requireNonNull(source, "source");
        this.degraded = degraded;
        this.noticeCode = normalizeNullable(noticeCode);
    }

    public String getContentVersion() { return contentVersion; }
    public List<PortfolioRetrievedSubject> getSubjects() { return subjects; }
    public List<PortfolioRetrievedPassage> getPassages() { return passages; }
    public PortfolioRetrievalSource getSource() { return source; }
    public boolean isDegraded() { return degraded; }
    public String getNoticeCode() { return noticeCode; }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof PortfolioRetrievalResult that)) { return false; }
        return degraded == that.degraded
                && Objects.equals(contentVersion, that.contentVersion)
                && Objects.equals(subjects, that.subjects)
                && Objects.equals(passages, that.passages)
                && Objects.equals(source, that.source)
                && Objects.equals(noticeCode, that.noticeCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contentVersion, subjects, passages, source, degraded, noticeCode);
    }

    @Override
    public String toString() {
        return "PortfolioRetrievalResult{" + "contentVersion='" + contentVersion + '\''
                + ", subjectCount=" + subjects.size() + ", passageCount=" + passages.size()
                + ", source=" + source + ", degraded=" + degraded
                + ", noticeCode='" + noticeCode + '\'' + '}';
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
