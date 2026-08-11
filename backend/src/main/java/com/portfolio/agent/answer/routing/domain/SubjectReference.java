package com.portfolio.agent.answer.routing.domain;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectResolutionSource;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType;

import java.util.Objects;

public final class SubjectReference {

    private final SubjectType subjectType;
    private final String subjectId;
    private final SubjectResolutionSource resolutionSource;
    private final String contentVersion;

    public SubjectReference(
            SubjectType subjectType,
            String subjectId,
            SubjectResolutionSource resolutionSource,
            String contentVersion) {
        this.subjectType = Objects.requireNonNull(subjectType, "subjectType");
        this.subjectId = requireText(subjectId, "subjectId");
        this.resolutionSource = Objects.requireNonNull(resolutionSource, "resolutionSource");
        this.contentVersion = normalizeText(contentVersion);
        if (subjectType != SubjectType.RESULT && this.contentVersion == null) {
            throw new IllegalArgumentException("contentVersion is required for public subjects");
        }
    }

    public static SubjectReference project(String subjectId, String contentVersion) {
        return new SubjectReference(
                SubjectType.PROJECT, subjectId, SubjectResolutionSource.EXPLICIT_REFERENCE, contentVersion);
    }

    public static SubjectReference caseReference(String subjectId, String contentVersion) {
        return new SubjectReference(
                SubjectType.CASE, subjectId, SubjectResolutionSource.EXPLICIT_REFERENCE, contentVersion);
    }

    public static SubjectReference result(String subjectId) {
        return new SubjectReference(
                SubjectType.RESULT, subjectId, SubjectResolutionSource.STRUCTURED_RESULT, null);
    }

    public SubjectType getSubjectType() {
        return subjectType;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public SubjectResolutionSource getResolutionSource() {
        return resolutionSource;
    }

    public String getContentVersion() {
        return contentVersion;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SubjectReference that)) {
            return false;
        }
        return subjectType == that.subjectType
                && Objects.equals(subjectId, that.subjectId)
                && resolutionSource == that.resolutionSource
                && Objects.equals(contentVersion, that.contentVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subjectType, subjectId, resolutionSource, contentVersion);
    }

    @Override
    public String toString() {
        return "SubjectReference{subjectType=" + subjectType
                + ", resolutionSource=" + resolutionSource
                + ", hasContentVersion=" + (contentVersion != null) + '}';
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
