package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType;
import com.portfolio.agent.answer.routing.domain.TextAnchor;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class PageReferenceMarkerCatalogTest {

    @Test
    void acceptsOnlyConfiguredCompleteMarkersForTheirDeclaredSubjectType() {
        InputStream stream = getClass().getResourceAsStream("/routing/page-reference-markers.v1.json");
        PageReferenceMarkerCatalog catalog = PageReferenceMarkerCatalog.load(stream);

        assertThat(catalog.supports(new TextAnchor("这个项目", 1), "介绍这个项目", SubjectType.PROJECT)).isTrue();
        assertThat(catalog.supports(new TextAnchor("这个", 1), "介绍这个", SubjectType.PROJECT)).isFalse();
        assertThat(catalog.supports(new TextAnchor("这个项目", 1), "介绍这个项目", SubjectType.CASE)).isFalse();
    }
}
