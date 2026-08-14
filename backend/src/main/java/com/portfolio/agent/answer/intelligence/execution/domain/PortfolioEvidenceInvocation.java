package com.portfolio.agent.answer.intelligence.execution.domain;

import com.portfolio.agent.answer.intelligence.retrieval.CorpusBackend;
import com.portfolio.agent.answer.intelligence.retrieval.EffectiveRetrievalPlan;
import com.portfolio.agent.answer.intelligence.retrieval.RetrievalIntent;
import com.portfolio.agent.answer.intelligence.retrieval.SearchStrategy;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** The sole typed retrieval invocation crossing the P3 capability boundary. */
public final class PortfolioEvidenceInvocation {

    private final AuthorizedSubjectScope authorizedSubjectScope;
    private final List<FacetRetrievalProfile> facetProfiles;
    private final List<ComparisonDimensionProfile> comparisonDimensionProfiles;
    private final EvidenceSelectionPolicy evidenceSelectionPolicy;
    private final String expectedContentVersion;
    private final EffectiveRetrievalPlan retrievalPlan;

    public PortfolioEvidenceInvocation(
            AuthorizedSubjectScope authorizedSubjectScope,
            List<FacetRetrievalProfile> facetProfiles,
            List<ComparisonDimensionProfile> comparisonDimensionProfiles,
            EvidenceSelectionPolicy evidenceSelectionPolicy,
            String expectedContentVersion) {
        this(authorizedSubjectScope, facetProfiles, comparisonDimensionProfiles,
                evidenceSelectionPolicy, expectedContentVersion,
                defaultPlan(authorizedSubjectScope, expectedContentVersion));
    }

    public PortfolioEvidenceInvocation(
            AuthorizedSubjectScope authorizedSubjectScope,
            List<FacetRetrievalProfile> facetProfiles,
            List<ComparisonDimensionProfile> comparisonDimensionProfiles,
            EvidenceSelectionPolicy evidenceSelectionPolicy,
            String expectedContentVersion,
            EffectiveRetrievalPlan retrievalPlan) {
        this.authorizedSubjectScope = Objects.requireNonNull(
                authorizedSubjectScope, "authorizedSubjectScope");
        this.facetProfiles = List.copyOf(Objects.requireNonNull(facetProfiles, "facetProfiles"));
        this.comparisonDimensionProfiles = List.copyOf(
                Objects.requireNonNull(comparisonDimensionProfiles, "comparisonDimensionProfiles"));
        this.evidenceSelectionPolicy = Objects.requireNonNull(
                evidenceSelectionPolicy, "evidenceSelectionPolicy");
        this.expectedContentVersion = requireText(expectedContentVersion, "expectedContentVersion");
        this.retrievalPlan = Objects.requireNonNull(retrievalPlan, "retrievalPlan");
        if (!this.expectedContentVersion.equals(retrievalPlan.getExpectedContentVersion())) {
            throw new IllegalArgumentException("invocation content version conflicts with retrieval plan");
        }
        if (!this.expectedContentVersion.equals(authorizedSubjectScope.getContentVersion())) {
            throw new IllegalArgumentException("invocation content version conflicts with subject scope");
        }
        assertDistinctFacets();
        assertDistinctDimensions();
        if (this.facetProfiles.isEmpty() && this.comparisonDimensionProfiles.isEmpty()) {
            throw new IllegalArgumentException("invocation must contain a retrieval profile");
        }
    }

    public AuthorizedSubjectScope getAuthorizedSubjectScope() {
        return authorizedSubjectScope;
    }

    public List<FacetRetrievalProfile> getFacetProfiles() {
        return facetProfiles;
    }

    public List<ComparisonDimensionProfile> getComparisonDimensionProfiles() {
        return comparisonDimensionProfiles;
    }

    public EvidenceSelectionPolicy getEvidenceSelectionPolicy() {
        return evidenceSelectionPolicy;
    }

    public String getExpectedContentVersion() {
        return expectedContentVersion;
    }

    public EffectiveRetrievalPlan getRetrievalPlan() {
        return retrievalPlan;
    }

    public PortfolioEvidenceInvocation withRetrievalPlan(EffectiveRetrievalPlan plan) {
        return new PortfolioEvidenceInvocation(authorizedSubjectScope, facetProfiles,
                comparisonDimensionProfiles, evidenceSelectionPolicy, expectedContentVersion,
                Objects.requireNonNull(plan, "plan"));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PortfolioEvidenceInvocation that)) {
            return false;
        }
        return authorizedSubjectScope.equals(that.authorizedSubjectScope)
                && facetProfiles.equals(that.facetProfiles)
                && comparisonDimensionProfiles.equals(that.comparisonDimensionProfiles)
                && evidenceSelectionPolicy.equals(that.evidenceSelectionPolicy)
                && expectedContentVersion.equals(that.expectedContentVersion)
                && retrievalPlan.equals(that.retrievalPlan);
    }

    @Override
    public int hashCode() {
        return Objects.hash(authorizedSubjectScope, facetProfiles,
                comparisonDimensionProfiles, evidenceSelectionPolicy, expectedContentVersion,
                retrievalPlan);
    }

    @Override
    public String toString() {
        return "PortfolioEvidenceInvocation{facetProfileCount=" + facetProfiles.size()
                + ", dimensionProfileCount=" + comparisonDimensionProfiles.size() + '}';
    }

    private void assertDistinctFacets() {
        LinkedHashSet<Object> values = new LinkedHashSet<>();
        for (FacetRetrievalProfile profile : facetProfiles) {
            if (!values.add(profile.getFacet())) {
                throw new IllegalArgumentException("facet profiles must be distinct");
            }
        }
    }

    private void assertDistinctDimensions() {
        LinkedHashSet<Object> values = new LinkedHashSet<>();
        for (ComparisonDimensionProfile profile : comparisonDimensionProfiles) {
            if (!values.add(profile.getDimension())) {
                throw new IllegalArgumentException("dimension profiles must be distinct");
            }
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private static EffectiveRetrievalPlan defaultPlan(
            AuthorizedSubjectScope scope, String expectedContentVersion) {
        boolean exact = scope.getMode() == AuthorizedSubjectScope.ScopeMode.EXACT_SUBJECTS;
        return new EffectiveRetrievalPlan(
                exact ? RetrievalIntent.EXACT_SUBJECT : RetrievalIntent.RECOMMENDATION_DISCOVERY,
                CorpusBackend.BUNDLE,
                exact ? SearchStrategy.EXACT : SearchStrategy.HYBRID,
                null,
                null,
                expectedContentVersion);
    }
}
