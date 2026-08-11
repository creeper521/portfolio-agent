package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationContext;
import com.portfolio.agent.answer.intelligence.domain.PortfolioReferenceContext;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTurn;
import com.portfolio.agent.answer.routing.domain.SemanticContext;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectResolutionSource;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType;
import com.portfolio.agent.answer.routing.domain.SubjectReference;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Migration seam for the old, single-subject context. It does not inspect
 * legacy conversation messages or any answer body.
 */
public final class LegacySemanticContextAdapter {

    private static final String UNKNOWN_LEGACY_VERSION = "legacy-unknown";

    /** Converts the actual legacy answer turn without inspecting its message window. */
    public SemanticContext adapt(PortfolioTurn legacyTurn) {
        Objects.requireNonNull(legacyTurn, "legacyTurn");
        PortfolioRecommendationContext recommendation = legacyTurn.getRecommendationContext();
        PortfolioReferenceContext reference = legacyTurn.getReferenceContext();
        String contentVersion = reference == null
                ? recommendation == null ? null : recommendation.getContentVersion()
                : reference.getPreviousContentVersion();
        LegacyContext context = LegacyContext.ofWithTypedReferences(
                legacyTurn.getProjectSlug(),
                legacyTurn.getCaseSlug(),
                recommendation == null ? List.of() : recommendation.getSelectedPortfolioIds(),
                reference == null ? List.of() : reference.getProjectSlugs(),
                reference == null ? List.of() : reference.getCaseSlugs(),
                legacyTurn.getAudienceRole(),
                legacyTurn.getSource(),
                Set.of(),
                contentVersion);
        return adapt(context);
    }

    public SemanticContext adapt(LegacyContext legacyContext) {
        Objects.requireNonNull(legacyContext, "legacyContext");
        if (legacyContext.getProjectSlug() != null && legacyContext.getCaseSlug() != null) {
            throw new IllegalArgumentException("legacy context cannot contain both project and case subjects");
        }

        String contentVersion = legacyContext.effectiveContentVersion();
        List<SubjectReference> activeSubjects = new ArrayList<>();
        if (legacyContext.getProjectSlug() != null) {
            activeSubjects.add(reference(
                    SubjectType.PROJECT, legacyContext.getProjectSlug(),
                    SubjectResolutionSource.ACTIVE_SUBJECT, contentVersion));
        }
        if (legacyContext.getCaseSlug() != null) {
            activeSubjects.add(reference(
                    SubjectType.CASE, legacyContext.getCaseSlug(),
                    SubjectResolutionSource.ACTIVE_SUBJECT, contentVersion));
        }

        List<SubjectReference> resultReferences = new ArrayList<>();
        for (String subjectId : legacyContext.getRecommendationSubjectIds()) {
            resultReferences.add(reference(
                    SubjectType.PROJECT, subjectId,
                    SubjectResolutionSource.STRUCTURED_RESULT, contentVersion));
        }
        for (String subjectId : legacyContext.getReferenceProjectIds()) {
            resultReferences.add(reference(
                    SubjectType.PROJECT, subjectId,
                    SubjectResolutionSource.STRUCTURED_RESULT, contentVersion));
        }
        for (String subjectId : legacyContext.getReferenceCaseIds()) {
            resultReferences.add(reference(
                    SubjectType.CASE, subjectId,
                    SubjectResolutionSource.STRUCTURED_RESULT, contentVersion));
        }
        return SemanticContext.of(
                unique(activeSubjects), unique(resultReferences), null,
                legacyContext.getAudienceRole(), legacyContext.getRequestSource(),
                legacyContext.getCoveredTopics());
    }

    public ContextMergeResult merge(SemanticContext semanticContext, LegacyContext legacyContext) {
        if (semanticContext == null && legacyContext == null) {
            return ContextMergeResult.resolved(SemanticContext.empty());
        }
        if (semanticContext == null) {
            return ContextMergeResult.resolved(adapt(legacyContext));
        }
        if (legacyContext == null) {
            return ContextMergeResult.resolved(semanticContext);
        }

        SemanticContext adaptedLegacy = adapt(legacyContext);
        if (!subjectIdentities(semanticContext).equals(subjectIdentities(adaptedLegacy))) {
            return ContextMergeResult.conflict();
        }
        return ContextMergeResult.resolved(semanticContext);
    }

    private Set<SubjectIdentity> subjectIdentities(SemanticContext context) {
        Set<SubjectIdentity> identities = new LinkedHashSet<>();
        addIdentities(identities, context.getActiveSubjects());
        addIdentities(identities, context.getResultReferences());
        context.getPendingPlanReference().ifPresent(reference -> addIdentities(identities, reference.getSubjects()));
        return Set.copyOf(identities);
    }

    private void addIdentities(Set<SubjectIdentity> identities, List<SubjectReference> references) {
        for (SubjectReference reference : references) {
            identities.add(new SubjectIdentity(reference.getSubjectType(), reference.getSubjectId()));
        }
    }

    private SubjectReference reference(
            SubjectType subjectType,
            String subjectId,
            SubjectResolutionSource source,
            String contentVersion) {
        return new SubjectReference(subjectType, subjectId, source, contentVersion);
    }

    private List<SubjectReference> unique(List<SubjectReference> references) {
        return List.copyOf(new LinkedHashSet<>(references));
    }

    public static final class LegacyContext {

        private final String projectSlug;
        private final String caseSlug;
        private final List<String> recommendationSubjectIds;
        private final List<String> referenceProjectIds;
        private final List<String> referenceCaseIds;
        private final String audienceRole;
        private final String requestSource;
        private final Set<String> coveredTopics;
        private final String contentVersion;

        private LegacyContext(
                String projectSlug,
                String caseSlug,
                List<String> recommendationSubjectIds,
                List<String> referenceProjectIds,
                List<String> referenceCaseIds,
                String audienceRole,
                String requestSource,
                Set<String> coveredTopics,
                String contentVersion) {
            this.projectSlug = normalizeText(projectSlug);
            this.caseSlug = normalizeText(caseSlug);
            this.recommendationSubjectIds = copyTexts(
                    recommendationSubjectIds, "recommendationSubjectIds");
            this.referenceProjectIds = copyTexts(referenceProjectIds, "referenceProjectIds");
            this.referenceCaseIds = copyTexts(referenceCaseIds, "referenceCaseIds");
            this.audienceRole = normalizeText(audienceRole);
            this.requestSource = normalizeText(requestSource);
            this.coveredTopics = copyTopics(coveredTopics);
            this.contentVersion = normalizeText(contentVersion);
        }

        /**
         * Compatibility factory for the initial intermediate shape. Its
         * reference list is explicitly project-typed; callers that have a
         * legacy case reference must use {@link #ofWithTypedReferences}.
         */
        public static LegacyContext of(
                String projectSlug,
                String caseSlug,
                List<String> recommendationSubjectIds,
                List<String> referenceSubjectIds,
                String audienceRole,
                String requestSource,
                Set<String> coveredTopics,
                String contentVersion) {
            return ofWithTypedReferences(
                    projectSlug, caseSlug, recommendationSubjectIds,
                    referenceSubjectIds, List.of(), audienceRole, requestSource,
                    coveredTopics, contentVersion);
        }

        public static LegacyContext ofWithTypedReferences(
                String projectSlug,
                String caseSlug,
                List<String> recommendationSubjectIds,
                List<String> referenceProjectIds,
                List<String> referenceCaseIds,
                String audienceRole,
                String requestSource,
                Set<String> coveredTopics,
                String contentVersion) {
            return new LegacyContext(
                    projectSlug, caseSlug, recommendationSubjectIds,
                    referenceProjectIds, referenceCaseIds, audienceRole,
                    requestSource, coveredTopics, contentVersion);
        }

        public String getProjectSlug() {
            return projectSlug;
        }

        public String getCaseSlug() {
            return caseSlug;
        }

        public List<String> getRecommendationSubjectIds() {
            return recommendationSubjectIds;
        }

        /**
         * @deprecated use the typed project/case getters for legacy reference context.
         */
        @Deprecated
        public List<String> getReferenceSubjectIds() {
            return referenceProjectIds;
        }

        public List<String> getReferenceProjectIds() {
            return referenceProjectIds;
        }

        public List<String> getReferenceCaseIds() {
            return referenceCaseIds;
        }

        public String getAudienceRole() {
            return audienceRole;
        }

        public String getRequestSource() {
            return requestSource;
        }

        public Set<String> getCoveredTopics() {
            return coveredTopics;
        }

        public String getContentVersion() {
            return contentVersion;
        }

        private String effectiveContentVersion() {
            return contentVersion == null ? UNKNOWN_LEGACY_VERSION : contentVersion;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof LegacyContext that)) {
                return false;
            }
            return Objects.equals(projectSlug, that.projectSlug)
                    && Objects.equals(caseSlug, that.caseSlug)
                    && Objects.equals(recommendationSubjectIds, that.recommendationSubjectIds)
                    && Objects.equals(referenceProjectIds, that.referenceProjectIds)
                    && Objects.equals(referenceCaseIds, that.referenceCaseIds)
                    && Objects.equals(audienceRole, that.audienceRole)
                    && Objects.equals(requestSource, that.requestSource)
                    && Objects.equals(coveredTopics, that.coveredTopics)
                    && Objects.equals(contentVersion, that.contentVersion);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    projectSlug, caseSlug, recommendationSubjectIds,
                    referenceProjectIds, referenceCaseIds, audienceRole,
                    requestSource, coveredTopics, contentVersion);
        }

        @Override
        public String toString() {
            return "LegacyContext{hasProjectSubject=" + (projectSlug != null)
                    + ", hasCaseSubject=" + (caseSlug != null)
                    + ", recommendationSubjectCount=" + recommendationSubjectIds.size()
                    + ", referenceProjectCount=" + referenceProjectIds.size()
                    + ", referenceCaseCount=" + referenceCaseIds.size()
                    + ", coveredTopicCount=" + coveredTopics.size() + '}';
        }
    }

    public static final class ContextMergeResult {

        private final boolean conflict;
        private final SemanticContext context;

        private ContextMergeResult(boolean conflict, SemanticContext context) {
            this.conflict = conflict;
            this.context = context;
        }

        public static ContextMergeResult resolved(SemanticContext context) {
            return new ContextMergeResult(false, Objects.requireNonNull(context, "context"));
        }

        public static ContextMergeResult conflict() {
            return new ContextMergeResult(true, null);
        }

        public boolean isConflict() {
            return conflict;
        }

        public SemanticContext getContext() {
            if (context == null) {
                throw new IllegalStateException("conflicting contexts do not have a canonical value");
            }
            return context;
        }
    }

    private static final class SubjectIdentity {

        private final SubjectType subjectType;
        private final String subjectId;

        private SubjectIdentity(SubjectType subjectType, String subjectId) {
            this.subjectType = subjectType;
            this.subjectId = subjectId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SubjectIdentity that)) {
                return false;
            }
            return subjectType == that.subjectType && Objects.equals(subjectId, that.subjectId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(subjectType, subjectId);
        }
    }

    private static List<String> copyTexts(List<String> values, String name) {
        Objects.requireNonNull(values, name);
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            normalized.add(requireText(value, name));
        }
        if (new LinkedHashSet<>(normalized).size() != normalized.size()) {
            throw new IllegalArgumentException(name + " must not contain duplicates");
        }
        return List.copyOf(normalized);
    }

    private static Set<String> copyTopics(Set<String> values) {
        Objects.requireNonNull(values, "coveredTopics");
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            normalized.add(requireText(value, "coveredTopics"));
        }
        return Set.copyOf(normalized);
    }

    private static String requireText(String value, String name) {
        String normalized = normalizeText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return normalized;
    }

    private static String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
