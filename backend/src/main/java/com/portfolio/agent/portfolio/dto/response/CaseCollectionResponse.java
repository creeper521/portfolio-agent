/**
 * 案例集合的公开响应载体。
 *
 * <p>包含 slug、标题、摘要与展示排序，由 PortfolioResponseMapper
 * 从领域 CaseCollection 映射，用于 GET /api/portfolio 的一级分组展示。
 */
package com.portfolio.agent.portfolio.dto.response;

import com.portfolio.agent.portfolio.domain.CaseCollection;

import java.util.Objects;

public final class CaseCollectionResponse {

    private final String slug;
    private final String title;
    private final String summary;
    private final int displayOrder;

    public CaseCollectionResponse(
            String slug,
            String title,
            String summary,
            int displayOrder
    ) {
        this.slug = slug;
        this.title = title;
        this.summary = summary;
        this.displayOrder = displayOrder;
    }

    public static CaseCollectionResponse from(CaseCollection collection) {
        return new CaseCollectionResponse(
                collection.getSlug(),
                collection.getTitle(),
                collection.getSummary(),
                collection.getDisplayOrder()
        );
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
        if (!(other instanceof CaseCollectionResponse that)) {
            return false;
        }
        return displayOrder == that.displayOrder
                && Objects.equals(slug, that.slug)
                && Objects.equals(title, that.title)
                && Objects.equals(summary, that.summary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(slug, title, summary, displayOrder);
    }
}
