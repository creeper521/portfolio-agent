package com.portfolio.agent.answer.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PortfolioAnswerSection {

    private final AnswerSectionType sectionType;
    private final String title;
    private final String content;
    private final List<String> claimIds;
    private final List<String> evidenceIds;
    private final List<PublicSourceReferenceValue> sourceReferences;

    public PortfolioAnswerSection(
            AnswerSectionType sectionType,
            String title,
            String content,
            List<String> claimIds,
            List<String> evidenceIds) {
        this(sectionType, title, content, claimIds, evidenceIds, List.of());
    }

    private PortfolioAnswerSection(
            AnswerSectionType sectionType,
            String title,
            String content,
            List<String> claimIds,
            List<String> evidenceIds,
            List<PublicSourceReferenceValue> sourceReferences) {
        this.sectionType = Objects.requireNonNull(sectionType, "sectionType");
        this.title = requireText(title, "title");
        this.content = requireText(content, "content");
        this.claimIds = stableDistinct(claimIds, "claimIds");
        this.evidenceIds = stableDistinct(evidenceIds, "evidenceIds");
        this.sourceReferences = stableDistinctReferences(sourceReferences);
    }

    /** Carries only strong public references across the presentation seam. */
    public static PortfolioAnswerSection grounded(
            AnswerSectionType sectionType, String title, String content,
            List<PublicSourceReferenceValue> sourceReferences) {
        return new PortfolioAnswerSection(sectionType, title, content,
                List.of(), List.of(), sourceReferences);
    }

    public AnswerSectionType getSectionType() { return sectionType; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public List<String> getClaimIds() { return claimIds; }
    public List<String> getEvidenceIds() { return evidenceIds; }
    public List<PublicSourceReferenceValue> getSourceReferences() { return sourceReferences; }

    private static List<PublicSourceReferenceValue> stableDistinctReferences(
            List<PublicSourceReferenceValue> values) {
        Objects.requireNonNull(values, "sourceReferences");
        java.util.LinkedHashMap<String, PublicSourceReferenceValue> references =
                new java.util.LinkedHashMap<>();
        for (PublicSourceReferenceValue value : values) {
            PublicSourceReferenceValue safe = Objects.requireNonNull(value, "sourceReference");
            if (references.putIfAbsent(safe.getReferenceKey(), safe) != null) {
                throw new IllegalArgumentException("sourceReferences contains duplicate referenceKey");
            }
        }
        return List.copyOf(references.values());
    }

    private static List<String> stableDistinct(List<String> values, String fieldName) {
        Objects.requireNonNull(values, fieldName);
        List<String> distinct = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(fieldName + " contains a blank id");
            }
            String trimmed = value.trim();
            if (!distinct.contains(trimmed)) {
                distinct.add(trimmed);
            }
        }
        return List.copyOf(distinct);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof PortfolioAnswerSection that)) { return false; }
        return sectionType == that.sectionType
                && Objects.equals(title, that.title)
                && Objects.equals(content, that.content)
                && Objects.equals(claimIds, that.claimIds)
                && Objects.equals(evidenceIds, that.evidenceIds)
                && Objects.equals(sourceReferences, that.sourceReferences);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sectionType, title, content, claimIds, evidenceIds, sourceReferences);
    }

    @Override
    public String toString() {
        return "PortfolioAnswerSection{sectionType=" + sectionType
                + ", title='" + title + '\''
                + ", claimCount=" + claimIds.size()
                + ", evidenceCount=" + evidenceIds.size()
                + ", sourceReferenceCount=" + sourceReferences.size() + '}';
    }
}
