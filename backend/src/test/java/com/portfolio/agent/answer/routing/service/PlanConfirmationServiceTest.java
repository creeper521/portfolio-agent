package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.domain.AgentTurnResult;
import com.portfolio.agent.answer.routing.adapter.crypto.JdkPlanCryptographyAdapter;
import com.portfolio.agent.answer.routing.domain.PlanConfirmation;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.SemanticTurnPlan;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.domain.TaskConfidence;
import com.portfolio.agent.answer.routing.domain.TaskOutcome;
import com.portfolio.agent.answer.routing.domain.TaskResultPayload;
import com.portfolio.agent.answer.routing.domain.TaskResultProvenance;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanConfirmationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-10T08:00:00Z");

    @Test
    void issuesTenMinuteChallengeAndVerifiesTheExactValidatedPlan() {
        PlanConfirmationService service = serviceAt(NOW);
        PlanConfirmation.VersionBinding versions = versions("public-v1", "subjects-v1", "capabilities-v1");

        PlanConfirmation.Challenge challenge = service.issue(validatedPlan(), versions);
        ConfirmationVerification verification = service.verify(challenge.toSubmission(), versions);

        assertEquals(NOW.plusSeconds(600), challenge.getExpiresAt());
        assertTrue(verification.isExecutable());
        assertEquals(PlanConfirmation.PlanInvalidationReason.NONE, verification.getReason());
        assertEquals("plan-01", verification.getValidatedPlan().orElseThrow().getPlanId());
    }

    @Test
    void rejectsTamperedSubmissionBeforeOtherInvalidationChecks() {
        PlanConfirmationService service = serviceAt(NOW);
        PlanConfirmation.VersionBinding versions = versions("public-v1", "subjects-v1", "capabilities-v1");
        PlanConfirmation.Challenge challenge = service.issue(validatedPlan(), versions);
        PlanConfirmation.Submission tampered = new PlanConfirmation.Submission(
                challenge.getConfirmationId(),
                challenge.getConfirmationPlan(),
                challenge.getPlanFingerprint() + "0",
                challenge.getIntegrityToken());

        ConfirmationVerification verification = service.verify(tampered,
                versions("public-v2", "subjects-v2", "capabilities-v2"));

        assertFalse(verification.isExecutable());
        assertEquals(PlanConfirmation.PlanInvalidationReason.PLAN_INTEGRITY_INVALID,
                verification.getReason());
    }

    @Test
    void expiryOnlyRequiresSamePlanResignButVersionChangesRequireReplan() {
        PlanConfirmation.VersionBinding initialVersions = versions("public-v1", "subjects-v1", "capabilities-v1");
        PlanConfirmation.Challenge challenge = serviceAt(NOW).issue(validatedPlan(), initialVersions);

        ConfirmationVerification expired = serviceAt(NOW.plusSeconds(600)).verify(
                challenge.toSubmission(), initialVersions);
        ConfirmationVerification contentChanged = serviceAt(NOW.plusSeconds(600)).verify(
                challenge.toSubmission(), versions("public-v2", "subjects-v1", "capabilities-v1"));

        assertEquals(PlanConfirmation.PlanInvalidationReason.PLAN_CONFIRMATION_EXPIRED, expired.getReason());
        assertTrue(expired.requiresSamePlanResign());
        assertFalse(expired.isExecutable());
        assertTrue(expired.getValidatedPlan().isPresent());
        PlanConfirmation.Challenge resigned = serviceAt(NOW.plusSeconds(600)).reissue(expired, initialVersions);
        assertNotEquals(challenge.getConfirmationId(), resigned.getConfirmationId());
        assertNotEquals(challenge.getIntegrityToken(), resigned.getIntegrityToken());
        assertEquals(challenge.getPlanFingerprint(), resigned.getPlanFingerprint());
        assertEquals(NOW.plusSeconds(600), resigned.getIssuedAt());
        PlanConfirmation.Challenge resignedViaSubmission = serviceAt(NOW.plusSeconds(600)).reissue(
                challenge.toSubmission(), initialVersions);
        assertNotEquals(challenge.getConfirmationId(), resignedViaSubmission.getConfirmationId());
        assertEquals(challenge.getPlanFingerprint(), resignedViaSubmission.getPlanFingerprint());
        assertEquals(PlanConfirmation.PlanInvalidationReason.CONTENT_VERSION_CHANGED, contentChanged.getReason());
        assertTrue(contentChanged.requiresReplan());
        assertFalse(contentChanged.getValidatedPlan().isPresent());
        assertThrows(IllegalArgumentException.class,
                () -> serviceAt(NOW.plusSeconds(1)).reissue(contentChanged, initialVersions));
    }

    @Test
    void evaluatesSubjectAndCapabilityVersionsAfterContentVersion() {
        PlanConfirmation.VersionBinding initialVersions = versions("public-v1", "subjects-v1", "capabilities-v1");
        PlanConfirmation.Challenge challenge = serviceAt(NOW).issue(validatedPlan(), initialVersions);

        ConfirmationVerification subjectChanged = serviceAt(NOW.plusSeconds(1)).verify(
                challenge.toSubmission(), versions("public-v1", "subjects-v2", "capabilities-v2"));
        ConfirmationVerification capabilityChanged = serviceAt(NOW.plusSeconds(1)).verify(
                challenge.toSubmission(), versions("public-v1", "subjects-v1", "capabilities-v2"));

        assertEquals(PlanConfirmation.PlanInvalidationReason.SUBJECT_REFERENCE_INVALIDATED,
                subjectChanged.getReason());
        assertEquals(PlanConfirmation.PlanInvalidationReason.CAPABILITY_SET_CHANGED,
                capabilityChanged.getReason());
    }

    @Test
    void rejectsUnsupportedSchemaBeforeCheckingExpiryOrRuntimeVersions() {
        PlanConfirmation.VersionBinding initialVersions = versions("public-v1", "subjects-v1", "capabilities-v1");
        PlanConfirmation.Challenge challenge = serviceAt(NOW).issue(validatedPlan(), initialVersions);

        ConfirmationVerification verification = serviceAt(NOW.plusSeconds(601)).verify(
                challenge.toSubmission(), new PlanConfirmation.VersionBinding(
                        "stp-v2", "public-v2", "subjects-v2", "capabilities-v2"));

        assertEquals(PlanConfirmation.PlanInvalidationReason.PLAN_SCHEMA_UNSUPPORTED,
                verification.getReason());
        assertTrue(verification.requiresReplan());
    }

    @Test
    void executeVerifiedRunsOnlyAnIntactCurrentConfirmation() {
        PlanConfirmation.VersionBinding initialVersions = versions(
                "public-v1", "subjects-v1", "capabilities-v1");
        PlanConfirmation.Challenge challenge = serviceAt(NOW).issue(validatedPlan(), initialVersions);
        AtomicInteger executions = new AtomicInteger();
        SemanticTurnCoordinator coordinator = new SemanticTurnCoordinator(List.of(new SemanticTaskExecutor() {
            @Override
            public SemanticRoutingTypes.TaskSourceDomain getSourceDomain() {
                return SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO;
            }

            @Override
            public TaskOutcome execute(SemanticTask task, List<TaskOutcome> availableDependencyOutcomes) {
                executions.incrementAndGet();
                return TaskOutcome.answered(
                        task.getTaskId(),
                        SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                        new TaskResultPayload.SectionResultPayload(List.of("safe"), "safe"),
                        TaskResultProvenance.direct(
                                SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                                List.of(), List.of()),
                        false);
            }
        }));

        AgentTurnResult success = serviceAt(NOW).executeVerified(
                challenge.toSubmission(), initialVersions, coordinator);
        PlanConfirmation.Submission tampered = new PlanConfirmation.Submission(
                challenge.getConfirmationId(), challenge.getConfirmationPlan(),
                challenge.getPlanFingerprint() + "0", challenge.getIntegrityToken());
        AgentTurnResult tamper = serviceAt(NOW).executeVerified(tampered, initialVersions, coordinator);
        AgentTurnResult expired = serviceAt(NOW.plusSeconds(600)).executeVerified(
                challenge.toSubmission(), initialVersions, coordinator);
        AgentTurnResult versionChanged = serviceAt(NOW).executeVerified(
                challenge.toSubmission(), versions("public-v2", "subjects-v1", "capabilities-v1"), coordinator);

        assertEquals(AgentTurnResult.Disposition.READY, success.getDisposition());
        assertEquals(1, executions.get());
        assertEquals(PlanConfirmation.PlanInvalidationReason.PLAN_INTEGRITY_INVALID,
                tamper.getInvalidationReason().orElseThrow());
        assertEquals(PlanConfirmation.PlanInvalidationReason.PLAN_CONFIRMATION_EXPIRED,
                expired.getInvalidationReason().orElseThrow());
        assertEquals(PlanConfirmation.PlanInvalidationReason.CONTENT_VERSION_CHANGED,
                versionChanged.getInvalidationReason().orElseThrow());
        assertEquals(1, executions.get());
    }

    private static PlanConfirmationService serviceAt(Instant now) {
        return new PlanConfirmationService(
                new JdkPlanCryptographyAdapter(key((byte) 3), key((byte) 4)),
                new SemanticPlanValidator(new PlanFingerprintService()),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private static ValidatedSemanticTurnPlan validatedPlan() {
        SubjectReference subject = SubjectReference.project("project-a", "public-v1");
        SemanticTask task = SemanticTask.create(
                "task-01",
                SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT,
                SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                "Review project A",
                new SemanticTaskParameters.PortfolioFact(subject, Set.of(), "INTERVIEWER"),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                TaskConfidence.highRule(),
                List.of(subject));
        SemanticTurnPlan candidate = new SemanticTurnPlan(
                "plan-01",
                "public-v1",
                SemanticTurnPlan.PlanSource.RULE,
                List.of(task),
                List.of(),
                List.of(),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                SemanticTurnPlan.PlanConfirmationPolicy.confirmationRequired(Set.of(
                        SemanticTurnPlan.ConfirmationTrigger.TASK_COUNT_REQUIRES_CONFIRMATION)));
        PlanFingerprintService fingerprints = new PlanFingerprintService();
        return new SemanticPlanValidator(fingerprints).validate(candidate, "stp-v1")
                .getValidatedPlan().orElseThrow();
    }

    private static PlanConfirmation.VersionBinding versions(
            String contentVersion, String subjectVersion, String capabilityVersion) {
        return new PlanConfirmation.VersionBinding(
                "stp-v1", contentVersion, subjectVersion, capabilityVersion);
    }

    private static byte[] key(byte value) {
        byte[] key = new byte[32];
        for (int index = 0; index < key.length; index++) {
            key[index] = value;
        }
        return key;
    }
}
