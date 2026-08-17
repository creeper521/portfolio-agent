package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.SemanticContext;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectResolutionSource;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationScopePolicyTest {

    private final RecommendationScopePolicy policy = new RecommendationScopePolicy();

    @Test
    void doesNotLetPageOrActiveContextConstrainABroadRecommendation() {
        ResolvedRoutingContext activeProject = ResolvedRoutingContext.resolved(
                List.of(project("project-a")), SubjectResolutionSource.ACTIVE_SUBJECT, SemanticContext.empty());

        assertThat(policy.authorizedSubjects(activeProject)).isEmpty();
    }

    @Test
    void permitsAnExplicitlyResolvedProjectToConstrainRecommendationScope() {
        ResolvedRoutingContext explicitProject = ResolvedRoutingContext.resolved(
                List.of(project("project-a")), SubjectResolutionSource.EXPLICIT_TEXT, SemanticContext.empty());

        assertThat(policy.authorizedSubjects(explicitProject))
                .extracting(SubjectReference::getSubjectId)
                .containsExactly("project-a");
    }

    private static SubjectReference project(String subjectId) {
        return new SubjectReference(
                SubjectType.PROJECT, subjectId, SubjectResolutionSource.EXPLICIT_REFERENCE, "content-v1");
    }
}
