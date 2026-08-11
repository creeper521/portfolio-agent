package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.domain.AgentTurnResult;
import com.portfolio.agent.answer.routing.adapter.crypto.PlanCryptographyPort;
import com.portfolio.agent.answer.routing.domain.ExecutionSelection;
import com.portfolio.agent.answer.routing.domain.PlanConfirmation;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/**
 * Issues and verifies stateless confirmation challenges. Verification is
 * deliberately ordered so an integrity failure never reveals version state.
 */
public final class PlanConfirmationService {

    private static final String SUPPORTED_SCHEMA = "stp-v1";
    private static final Duration CONFIRMATION_TTL = Duration.ofMinutes(10);
    private static final int CONFIRMATION_ID_BYTES = 18;

    private final PlanCryptographyPort cryptography;
    private final SemanticPlanValidator validator;
    private final Clock clock;
    private final SecureRandom secureRandom;

    public PlanConfirmationService(
            PlanCryptographyPort cryptography,
            SemanticPlanValidator validator,
            Clock clock) {
        this(cryptography, validator, clock, new SecureRandom());
    }

    PlanConfirmationService(
            PlanCryptographyPort cryptography,
            SemanticPlanValidator validator,
            Clock clock,
            SecureRandom secureRandom) {
        this.cryptography = Objects.requireNonNull(cryptography, "cryptography");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    public PlanConfirmation.Challenge issue(
            ValidatedSemanticTurnPlan plan,
            PlanConfirmation.VersionBinding currentVersions) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(currentVersions, "currentVersions");
        if (!SUPPORTED_SCHEMA.equals(currentVersions.getSchemaVersion())
                || !plan.getContentVersion().equals(currentVersions.getContentVersion())) {
            throw new IllegalArgumentException("plan confirmation cannot be issued for current versions");
        }
        Instant issuedAt = clock.instant();
        PlanConfirmation.Identity identity = new PlanConfirmation.Identity(
                randomConfirmationId(),
                issuedAt,
                issuedAt.plus(CONFIRMATION_TTL),
                plan.getPlanFingerprint());
        PlanCryptographyPort.SealedPlan sealed = cryptography.seal(plan, identity, currentVersions);
        return new PlanConfirmation.Challenge(identity, sealed.getConfirmationPlan(), sealed.getIntegrityToken());
    }

    public ConfirmationVerification verify(
            PlanConfirmation.Submission submission,
            PlanConfirmation.VersionBinding currentVersions) {
        Objects.requireNonNull(currentVersions, "currentVersions");
        if (submission == null || !cryptography.isIntegrityValid(submission)) {
            return ConfirmationVerification.integrityInvalid();
        }

        PlanCryptographyPort.OpenedPlan opened;
        try {
            opened = cryptography.open(submission);
        } catch (IllegalArgumentException exception) {
            return ConfirmationVerification.integrityInvalid();
        }
        if (!SUPPORTED_SCHEMA.equals(opened.getVersionBinding().getSchemaVersion())
                || !opened.getVersionBinding().getSchemaVersion().equals(currentVersions.getSchemaVersion())) {
            return ConfirmationVerification.invalid(PlanConfirmation.PlanInvalidationReason.PLAN_SCHEMA_UNSUPPORTED);
        }

        PlanValidationResult validation = validator.validate(
                opened.getPlan(), opened.getVersionBinding().getSchemaVersion());
        if (!validation.isValid()
                || validation.getValidatedPlan().isEmpty()
                || !opened.getIdentity().getPlanFingerprint().equals(submission.getPlanFingerprint())
                || !validation.getValidatedPlan().orElseThrow().getPlanFingerprint()
                        .equals(opened.getIdentity().getPlanFingerprint())) {
            return ConfirmationVerification.integrityInvalid();
        }

        boolean expired = !clock.instant().isBefore(opened.getIdentity().getExpiresAt());
        if (!opened.getVersionBinding().getContentVersion().equals(currentVersions.getContentVersion())) {
            return ConfirmationVerification.replan(PlanConfirmation.PlanInvalidationReason.CONTENT_VERSION_CHANGED);
        }
        if (!opened.getVersionBinding().getSubjectVersion().equals(currentVersions.getSubjectVersion())) {
            return ConfirmationVerification.replan(
                    PlanConfirmation.PlanInvalidationReason.SUBJECT_REFERENCE_INVALIDATED);
        }
        if (!opened.getVersionBinding().getCapabilitySetVersion()
                .equals(currentVersions.getCapabilitySetVersion())) {
            return ConfirmationVerification.replan(
                    PlanConfirmation.PlanInvalidationReason.CAPABILITY_SET_CHANGED);
        }
        if (expired) {
            return ConfirmationVerification.expired(validation.getValidatedPlan().orElseThrow());
        }
        return ConfirmationVerification.executable(validation.getValidatedPlan().orElseThrow());
    }

    /**
     * The only public confirmation execution boundary. It retains verification
     * internals within this service and never promotes an unverified client
     * envelope into coordinator input.
     */
    public AgentTurnResult executeVerified(
            PlanConfirmation.Submission submission,
            PlanConfirmation.VersionBinding currentVersions,
            SemanticTurnCoordinator coordinator) {
        Objects.requireNonNull(currentVersions, "currentVersions");
        Objects.requireNonNull(coordinator, "coordinator");
        ConfirmationVerification verification = verify(submission, currentVersions);
        if (!verification.isExecutable()) {
            return AgentTurnResult.planInvalidated(verification.getReason());
        }
        ValidatedSemanticTurnPlan plan = verification.getValidatedPlan().orElseThrow();
        java.util.LinkedHashSet<String> taskIds = new java.util.LinkedHashSet<>();
        for (com.portfolio.agent.answer.routing.domain.SemanticTask task : plan.getTasks()) {
            taskIds.add(task.getTaskId());
        }
        return AgentTurnResult.ready(
                plan.getPlan(), coordinator.execute(plan, ExecutionSelection.allExecutable(taskIds)));
    }

    /**
     * Re-signs only a verification that was rejected solely because its
     * ten-minute confirmation window elapsed. The validated plan is retained
     * by that verification, so no untrusted client payload is promoted again.
     */
    public PlanConfirmation.Challenge reissue(
            PlanConfirmation.Submission submission,
            PlanConfirmation.VersionBinding currentVersions) {
        ConfirmationVerification verification = verify(submission, currentVersions);
        return reissue(verification, currentVersions);
    }

    PlanConfirmation.Challenge reissue(
            ConfirmationVerification verification,
            PlanConfirmation.VersionBinding currentVersions) {
        Objects.requireNonNull(verification, "verification");
        Objects.requireNonNull(currentVersions, "currentVersions");
        if (!verification.requiresSamePlanResign() || verification.getValidatedPlan().isEmpty()) {
            throw new IllegalArgumentException("only an expired confirmation can be reissued");
        }
        return issue(verification.getValidatedPlan().orElseThrow(), currentVersions);
    }

    private String randomConfirmationId() {
        byte[] bytes = new byte[CONFIRMATION_ID_BYTES];
        secureRandom.nextBytes(bytes);
        return "confirm-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

final class ConfirmationVerification {

    private final PlanConfirmation.PlanInvalidationReason reason;
    private final ValidatedSemanticTurnPlan validatedPlan;

    private ConfirmationVerification(
            PlanConfirmation.PlanInvalidationReason reason,
            ValidatedSemanticTurnPlan validatedPlan) {
        this.reason = Objects.requireNonNull(reason, "reason");
        this.validatedPlan = validatedPlan;
        boolean executable = reason == PlanConfirmation.PlanInvalidationReason.NONE;
        boolean carriesResignablePlan = reason == PlanConfirmation.PlanInvalidationReason.PLAN_CONFIRMATION_EXPIRED;
        if ((validatedPlan != null) != (executable || carriesResignablePlan)) {
            throw new IllegalArgumentException(
                    "only executable or expiry-only verification may carry a validated plan");
        }
    }

    static ConfirmationVerification executable(ValidatedSemanticTurnPlan validatedPlan) {
        return new ConfirmationVerification(PlanConfirmation.PlanInvalidationReason.NONE,
                Objects.requireNonNull(validatedPlan, "validatedPlan"));
    }

    static ConfirmationVerification integrityInvalid() {
        return invalid(PlanConfirmation.PlanInvalidationReason.PLAN_INTEGRITY_INVALID);
    }

    static ConfirmationVerification expired(ValidatedSemanticTurnPlan validatedPlan) {
        return new ConfirmationVerification(
                PlanConfirmation.PlanInvalidationReason.PLAN_CONFIRMATION_EXPIRED,
                Objects.requireNonNull(validatedPlan, "validatedPlan"));
    }

    static ConfirmationVerification replan(PlanConfirmation.PlanInvalidationReason reason) {
        if (reason != PlanConfirmation.PlanInvalidationReason.CONTENT_VERSION_CHANGED
                && reason != PlanConfirmation.PlanInvalidationReason.SUBJECT_REFERENCE_INVALIDATED
                && reason != PlanConfirmation.PlanInvalidationReason.CAPABILITY_SET_CHANGED) {
            throw new IllegalArgumentException("reason requires a full replan");
        }
        return invalid(reason);
    }

    static ConfirmationVerification invalid(PlanConfirmation.PlanInvalidationReason reason) {
        if (reason == PlanConfirmation.PlanInvalidationReason.NONE) {
            throw new IllegalArgumentException("invalid verification needs an invalidation reason");
        }
        return new ConfirmationVerification(reason, null);
    }

    boolean isExecutable() {
        return reason == PlanConfirmation.PlanInvalidationReason.NONE;
    }

    boolean requiresSamePlanResign() {
        return reason == PlanConfirmation.PlanInvalidationReason.PLAN_CONFIRMATION_EXPIRED;
    }

    boolean requiresReplan() {
        return reason == PlanConfirmation.PlanInvalidationReason.PLAN_SCHEMA_UNSUPPORTED
                || reason == PlanConfirmation.PlanInvalidationReason.CONTENT_VERSION_CHANGED
                || reason == PlanConfirmation.PlanInvalidationReason.SUBJECT_REFERENCE_INVALIDATED
                || reason == PlanConfirmation.PlanInvalidationReason.CAPABILITY_SET_CHANGED;
    }

    PlanConfirmation.PlanInvalidationReason getReason() {
        return reason;
    }

    Optional<ValidatedSemanticTurnPlan> getValidatedPlan() {
        return Optional.ofNullable(validatedPlan);
    }

    @Override
    public String toString() {
        return "ConfirmationVerification{executable=" + isExecutable() + ", reason=" + reason + '}';
    }
}
