package com.portfolio.agent.turn.projection;

import java.util.Objects;

public final class PublicSection {
    private final String sectionId;
    private final Kind sectionKind;
    private final String title;
    private final String content;
    private final PublicSupport support;

    public PublicSection(
            String sectionId, Kind sectionKind, String title,
            String content, PublicSupport support) {
        this.sectionId = id(sectionId);
        this.sectionKind = Objects.requireNonNull(sectionKind, "sectionKind");
        this.title = text(title, "title");
        this.content = text(content, "content");
        this.support = Objects.requireNonNull(support, "support");
    }
    public String getSectionId() { return sectionId; }
    public Kind getSectionKind() { return sectionKind; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public PublicSupport getSupport() { return support; }

    public enum Kind {
        BACKGROUND, RESPONSIBILITY, SOLUTION, VERIFICATION, STATUS, BOUNDARY,
        GENERAL_PRINCIPLE, PORTFOLIO_EXAMPLE, RELATION
    }

    private static String id(String value) {
        if (value == null || !value.matches("[a-z0-9][a-z0-9-]{1,95}")) {
            throw new IllegalArgumentException("sectionId is invalid");
        }
        return value;
    }
    private static String text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
