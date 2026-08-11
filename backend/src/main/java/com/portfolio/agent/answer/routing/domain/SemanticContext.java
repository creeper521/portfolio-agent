package com.portfolio.agent.answer.routing.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Canonical, structured turn context. It deliberately excludes conversation
 * messages and answer text so routing never infers subjects from free text.
 */
public final class SemanticContext {

    private final List<SubjectReference> activeSubjects;
    private final List<SubjectReference> resultReferences;
    private final PendingPlanReference pendingPlanReference;
    private final String audienceRole;
    private final String requestSource;
    private final Set<String> coveredTopics;

    private SemanticContext(
            List<SubjectReference> activeSubjects,
            List<SubjectReference> resultReferences,
            PendingPlanReference pendingPlanReference,
            String audienceRole,
            String requestSource,
            Set<String> coveredTopics) {
        this.activeSubjects = copyReferences(activeSubjects, "activeSubjects");
        this.resultReferences = copyReferences(resultReferences, "resultReferences");
        this.pendingPlanReference = pendingPlanReference;
        this.audienceRole = normalizeText(audienceRole);
        this.requestSource = normalizeText(requestSource);
        this.coveredTopics = copyTopics(coveredTopics);
    }

    public static SemanticContext of(
            List<SubjectReference> activeSubjects,
            List<SubjectReference> resultReferences,
            PendingPlanReference pendingPlanReference,
            String audienceRole,
            String requestSource,
            Set<String> coveredTopics) {
        return new SemanticContext(
                activeSubjects, resultReferences, pendingPlanReference,
                audienceRole, requestSource, coveredTopics);
    }

    public static SemanticContext empty() {
        return of(List.of(), List.of(), null, null, null, Set.of());
    }

    public List<SubjectReference> getActiveSubjects() {
        return activeSubjects;
    }

    public List<SubjectReference> getResultReferences() {
        return resultReferences;
    }

    public Optional<PendingPlanReference> getPendingPlanReference() {
        return Optional.ofNullable(pendingPlanReference);
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SemanticContext that)) {
            return false;
        }
        return Objects.equals(activeSubjects, that.activeSubjects)
                && Objects.equals(resultReferences, that.resultReferences)
                && Objects.equals(pendingPlanReference, that.pendingPlanReference)
                && Objects.equals(audienceRole, that.audienceRole)
                && Objects.equals(requestSource, that.requestSource)
                && Objects.equals(coveredTopics, that.coveredTopics);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                activeSubjects, resultReferences, pendingPlanReference,
                audienceRole, requestSource, coveredTopics);
    }

    @Override
    public String toString() {
        return "SemanticContext{activeSubjectCount=" + activeSubjects.size()
                + ", resultReferenceCount=" + resultReferences.size()
                + ", hasPendingPlan=" + (pendingPlanReference != null)
                + ", coveredTopicCount=" + coveredTopics.size() + '}';
    }

    public static final class PendingPlanReference {

        private final String referenceId;
        private final String planFingerprint;
        private final List<SubjectReference> subjects;

        public PendingPlanReference(String referenceId, List<SubjectReference> subjects) {
            this(referenceId, null, subjects);
        }

        public PendingPlanReference(
                String referenceId,
                String planFingerprint,
                List<SubjectReference> subjects) {
            this.referenceId = requireText(referenceId, "referenceId");
            this.planFingerprint = normalizeText(planFingerprint);
            this.subjects = copyReferences(subjects, "subjects");
        }

        public String getReferenceId() {
            return referenceId;
        }

        public String getPlanFingerprint() {
            return planFingerprint;
        }

        public List<SubjectReference> getSubjects() {
            return subjects;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PendingPlanReference that)) {
                return false;
            }
            return Objects.equals(referenceId, that.referenceId)
                    && Objects.equals(planFingerprint, that.planFingerprint)
                    && Objects.equals(subjects, that.subjects);
        }

        @Override
        public int hashCode() {
            return Objects.hash(referenceId, planFingerprint, subjects);
        }

        @Override
        public String toString() {
            return "PendingPlanReference{hasFingerprint=" + (planFingerprint != null)
                    + ", subjectCount=" + subjects.size() + '}';
        }
    }

    private static List<SubjectReference> copyReferences(List<SubjectReference> values, String name) {
        List<SubjectReference> copied = List.copyOf(Objects.requireNonNull(values, name));
        if (new LinkedHashSet<>(copied).size() != copied.size()) {
            throw new IllegalArgumentException(name + " must not contain duplicates");
        }
        return copied;
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
