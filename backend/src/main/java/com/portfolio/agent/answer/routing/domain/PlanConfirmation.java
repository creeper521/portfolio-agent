package com.portfolio.agent.answer.routing.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable values exchanged by the stateless plan-confirmation protocol.
 * The opaque envelope and integrity token deliberately have no diagnostic
 * representation because neither value may enter logs or display DTOs.
 */
public final class PlanConfirmation {

    private PlanConfirmation() {
    }

    public enum PlanInvalidationReason {
        NONE,
        PLAN_INTEGRITY_INVALID,
        PLAN_SCHEMA_UNSUPPORTED,
        PLAN_CONFIRMATION_EXPIRED,
        CONTENT_VERSION_CHANGED,
        SUBJECT_REFERENCE_INVALIDATED,
        CAPABILITY_SET_CHANGED
    }

    public static final class Identity {

        private final String confirmationId;
        private final Instant issuedAt;
        private final Instant expiresAt;
        private final String planFingerprint;

        public Identity(
                String confirmationId,
                Instant issuedAt,
                Instant expiresAt,
                String planFingerprint) {
            this.confirmationId = requireText(confirmationId, "confirmationId");
            this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
            this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
            if (!this.expiresAt.isAfter(this.issuedAt)) {
                throw new IllegalArgumentException("expiresAt must be after issuedAt");
            }
            this.planFingerprint = requireText(planFingerprint, "planFingerprint");
        }

        public String getConfirmationId() {
            return confirmationId;
        }

        public Instant getIssuedAt() {
            return issuedAt;
        }

        public Instant getExpiresAt() {
            return expiresAt;
        }

        public String getPlanFingerprint() {
            return planFingerprint;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Identity that)) {
                return false;
            }
            return Objects.equals(confirmationId, that.confirmationId)
                    && Objects.equals(issuedAt, that.issuedAt)
                    && Objects.equals(expiresAt, that.expiresAt)
                    && Objects.equals(planFingerprint, that.planFingerprint);
        }

        @Override
        public int hashCode() {
            return Objects.hash(confirmationId, issuedAt, expiresAt, planFingerprint);
        }

        @Override
        public String toString() {
            return "PlanConfirmation.Identity{hasIdentity=true, hasFingerprint=true}";
        }
    }

    public static final class VersionBinding {

        private final String schemaVersion;
        private final String contentVersion;
        private final String subjectVersion;
        private final String capabilitySetVersion;

        public VersionBinding(
                String schemaVersion,
                String contentVersion,
                String subjectVersion,
                String capabilitySetVersion) {
            this.schemaVersion = requireText(schemaVersion, "schemaVersion");
            this.contentVersion = requireText(contentVersion, "contentVersion");
            this.subjectVersion = requireText(subjectVersion, "subjectVersion");
            this.capabilitySetVersion = requireText(capabilitySetVersion, "capabilitySetVersion");
        }

        public String getSchemaVersion() {
            return schemaVersion;
        }

        public String getContentVersion() {
            return contentVersion;
        }

        public String getSubjectVersion() {
            return subjectVersion;
        }

        public String getCapabilitySetVersion() {
            return capabilitySetVersion;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof VersionBinding that)) {
                return false;
            }
            return Objects.equals(schemaVersion, that.schemaVersion)
                    && Objects.equals(contentVersion, that.contentVersion)
                    && Objects.equals(subjectVersion, that.subjectVersion)
                    && Objects.equals(capabilitySetVersion, that.capabilitySetVersion);
        }

        @Override
        public int hashCode() {
            return Objects.hash(schemaVersion, contentVersion, subjectVersion, capabilitySetVersion);
        }

        @Override
        public String toString() {
            return "PlanConfirmation.VersionBinding{hasSchema=true, hasContentVersion=true, "
                    + "hasSubjectVersion=true, hasCapabilitySetVersion=true}";
        }
    }

    public static final class Challenge {

        private final Identity identity;
        private final String confirmationPlan;
        private final String integrityToken;

        public Challenge(Identity identity, String confirmationPlan, String integrityToken) {
            this.identity = Objects.requireNonNull(identity, "identity");
            this.confirmationPlan = requireText(confirmationPlan, "confirmationPlan");
            this.integrityToken = requireText(integrityToken, "integrityToken");
        }

        public String getConfirmationId() {
            return identity.getConfirmationId();
        }

        public Instant getIssuedAt() {
            return identity.getIssuedAt();
        }

        public Instant getExpiresAt() {
            return identity.getExpiresAt();
        }

        public String getPlanFingerprint() {
            return identity.getPlanFingerprint();
        }

        public String getConfirmationPlan() {
            return confirmationPlan;
        }

        public String getIntegrityToken() {
            return integrityToken;
        }

        public Submission toSubmission() {
            return new Submission(getConfirmationId(), confirmationPlan, getPlanFingerprint(), integrityToken);
        }

        @Override
        public String toString() {
            return "PlanConfirmation.Challenge{hasIdentity=true, hasOpaqueEnvelope=true, hasIntegrityToken=true}";
        }
    }

    public static final class Submission {

        private final String confirmationId;
        private final String confirmationPlan;
        private final String planFingerprint;
        private final String integrityToken;

        public Submission(
                String confirmationId,
                String confirmationPlan,
                String planFingerprint,
                String integrityToken) {
            this.confirmationId = requireText(confirmationId, "confirmationId");
            this.confirmationPlan = requireText(confirmationPlan, "confirmationPlan");
            this.planFingerprint = requireText(planFingerprint, "planFingerprint");
            this.integrityToken = requireText(integrityToken, "integrityToken");
        }

        public String getConfirmationId() {
            return confirmationId;
        }

        public String getConfirmationPlan() {
            return confirmationPlan;
        }

        public String getPlanFingerprint() {
            return planFingerprint;
        }

        public String getIntegrityToken() {
            return integrityToken;
        }

        @Override
        public String toString() {
            return "PlanConfirmation.Submission{hasIdentity=true, hasOpaqueEnvelope=true, hasIntegrityToken=true}";
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
