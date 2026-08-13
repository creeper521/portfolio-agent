package com.portfolio.agent.answer.intelligence.execution.domain;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectResolutionSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioExecutionDomainContractTest {

    @Test
    void emptyRecommendationCandidatesCompileToAllPublishedScope() {
        AuthorizedSubjectScope scope = AuthorizedSubjectScope.allPublishedCandidates("public-v1");

        assertEquals(AuthorizedSubjectScope.ScopeMode.ALL_PUBLISHED_CANDIDATES, scope.getMode());
        assertTrue(scope.getExactSubjects().isEmpty());
        assertTrue(scope.contains(SubjectReference.project("project-a", "public-v1")));
    }

    @Test
    void exactRecommendationRefinementCannotExpandSubjects() {
        SubjectReference projectA = SubjectReference.project("project-a", "public-v1");
        RecommendationScopeBinding binding = new RecommendationScopeBinding(
                AuthorizedSubjectScope.exactSubjects(List.of(projectA), "public-v1"), "public-v1");

        assertTrue(binding.canRefineTo(List.of(projectA)));
        assertTrue(!binding.canRefineTo(List.of(
                projectA, SubjectReference.project("project-b", "public-v1"))));
    }

    @Test
    void exactScopeMatchesPublicSubjectIdentityRegardlessOfResolutionSource() {
        SubjectReference activeSubject = new SubjectReference(
                SemanticRoutingTypes.SubjectType.CASE, "case-a",
                SubjectResolutionSource.ACTIVE_SUBJECT, "public-v1");
        AuthorizedSubjectScope scope = AuthorizedSubjectScope.exactSubjects(
                List.of(activeSubject), "public-v1");

        assertTrue(scope.contains(SubjectReference.caseReference("case-a", "public-v1")));
    }

    @Test
    void invocationUsesOnlyClosedProfilesAndMatchingVersion() {
        PortfolioEvidenceInvocation invocation = new PortfolioEvidenceInvocation(
                AuthorizedSubjectScope.exactSubjects(
                        List.of(SubjectReference.project("project-a", "public-v1")), "public-v1"),
                List.of(FacetRetrievalProfile.forFacet(SemanticRoutingTypes.PortfolioFacet.OVERVIEW)),
                List.of(), EvidenceSelectionPolicy.defaults(), "public-v1");

        assertEquals("public-v1", invocation.getExpectedContentVersion());
        assertThrows(IllegalArgumentException.class, () -> new PortfolioEvidenceInvocation(
                invocation.getAuthorizedSubjectScope(), invocation.getFacetProfiles(),
                invocation.getComparisonDimensionProfiles(), invocation.getEvidenceSelectionPolicy(),
                "public-v2"));
    }

    @Test
    void facetProfilesUseAnswerClaimCategoriesRatherThanRoutingLabels() {
        assertEquals(List.of("BACKGROUND"),
                FacetRetrievalProfile.forFacet(SemanticRoutingTypes.PortfolioFacet.OVERVIEW)
                        .getClaimCategories());
        assertEquals(List.of("LIMITATION", "REFLECTION"),
                FacetRetrievalProfile.forFacet(SemanticRoutingTypes.PortfolioFacet.CHALLENGE)
                        .getClaimCategories());
    }
}
