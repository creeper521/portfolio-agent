package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectResolutionSource;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MinimalTurnFallbackTest {

    private final MinimalTurnFallback fallback = new MinimalTurnFallback();

    @Test
    void onlyOffersAnOverviewForAnExactUniquePublicAlias() {
        List<SubjectReference> subjects = List.of(new SubjectReference(
                SubjectType.PROJECT, "project-a", SubjectResolutionSource.EXPLICIT_REFERENCE, "public-v1"));

        assertThat(fallback.resolve("介绍 project-a", subjects).getDisposition())
                .isEqualTo(MinimalTurnFallback.Disposition.NOT_APPLICABLE);
        assertThat(fallback.resolve("project-a", subjects).getDisposition())
                .isEqualTo(MinimalTurnFallback.Disposition.EXACT_ALIAS_OVERVIEW);
    }
}
