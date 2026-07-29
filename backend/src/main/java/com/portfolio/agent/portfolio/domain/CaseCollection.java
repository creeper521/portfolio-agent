package com.portfolio.agent.portfolio.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public final class CaseCollection {

    private final String id;
    private final String slug;
    private final String title;
    private final String summary;
    private final int displayOrder;

    @JsonCreator
    public CaseCollection(
            @JsonProperty("id") String id,
            @JsonProperty("slug") String slug,
            @JsonProperty("title") String title,
            @JsonProperty("summary") String summary,
            @JsonProperty("displayOrder") int displayOrder
    ) {
        this.id = id;
        this.slug = slug;
        this.title = title;
        this.summary = summary;
        this.displayOrder = displayOrder;
    }

    public String getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CaseCollection that)) {
            return false;
        }
        return displayOrder == that.displayOrder
                && Objects.equals(id, that.id)
                && Objects.equals(slug, that.slug)
                && Objects.equals(title, that.title)
                && Objects.equals(summary, that.summary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, slug, title, summary, displayOrder);
    }

    @Override
    public String toString() {
        return "CaseCollection{" +
                "id='" + id + '\'' +
                ", slug='" + slug + '\'' +
                ", title='" + title + '\'' +
                ", summary='" + summary + '\'' +
                ", displayOrder=" + displayOrder +
                '}';
    }
}
