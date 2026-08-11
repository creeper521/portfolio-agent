package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.adapter.crypto.JdkPlanCryptographyAdapter;
import com.portfolio.agent.answer.routing.adapter.crypto.PlanCryptographyPort;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.SemanticTurnPlan;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.domain.TaskConfidence;
import com.portfolio.agent.answer.routing.service.ValidatedSemanticTurnPlan;
import com.portfolio.agent.answer.routing.domain.PlanConfirmation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdkPlanCryptographyAdapterTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-08-10T08:00:00Z");

    @Test
    void sealsAndOpensPlanWithoutPlaintextJson() {
        JdkPlanCryptographyAdapter crypto = new JdkPlanCryptographyAdapter(
                key((byte) 1), key((byte) 2));
        ValidatedSemanticTurnPlan plan = validatedPlan();
        PlanConfirmation.Identity identity = new PlanConfirmation.Identity(
                "confirm-01", ISSUED_AT, ISSUED_AT.plusSeconds(600), plan.getPlanFingerprint());
        PlanConfirmation.VersionBinding versions = currentVersions();

        PlanCryptographyPort.SealedPlan sealed = crypto.seal(plan, identity, versions);
        PlanCryptographyPort.OpenedPlan opened = crypto.open(
                new PlanConfirmation.Submission(
                        identity.getConfirmationId(),
                        sealed.getConfirmationPlan(),
                        identity.getPlanFingerprint(),
                        sealed.getIntegrityToken()));

        assertFalse(sealed.getConfirmationPlan().contains("PORTFOLIO_FACT"));
        assertFalse(sealed.getConfirmationPlan().contains("project-a"));
        assertEquals(plan.getPlan(), opened.getPlan());
        assertEquals(identity, opened.getIdentity());
        assertEquals(versions, opened.getVersionBinding());
    }

    @Test
    void rejectsTamperedEnvelopeAndDoesNotAcceptInvalidKeyConfiguration() {
        JdkPlanCryptographyAdapter crypto = new JdkPlanCryptographyAdapter(
                key((byte) 1), key((byte) 2));
        ValidatedSemanticTurnPlan plan = validatedPlan();
        PlanConfirmation.Identity identity = new PlanConfirmation.Identity(
                "confirm-01", ISSUED_AT, ISSUED_AT.plusSeconds(600), plan.getPlanFingerprint());
        PlanCryptographyPort.SealedPlan sealed = crypto.seal(plan, identity, currentVersions());
        String replacement = sealed.getConfirmationPlan().startsWith("A") ? "B" : "A";
        String tamperedPlan = replacement + sealed.getConfirmationPlan().substring(1);
        PlanConfirmation.Submission tampered = new PlanConfirmation.Submission(
                identity.getConfirmationId(), tamperedPlan, identity.getPlanFingerprint(), sealed.getIntegrityToken());

        assertFalse(crypto.isIntegrityValid(tampered));
        assertThrows(IllegalArgumentException.class,
                () -> new JdkPlanCryptographyAdapter(new byte[16], key((byte) 2)));
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
        return new SemanticPlanValidator(new PlanFingerprintService()).validate(candidate, "stp-v1")
                .getValidatedPlan().orElseThrow();
    }

    private static PlanConfirmation.VersionBinding currentVersions() {
        return new PlanConfirmation.VersionBinding("stp-v1", "public-v1", "subjects-v1", "capabilities-v1");
    }

    private static byte[] key(byte value) {
        byte[] key = new byte[32];
        for (int index = 0; index < key.length; index++) {
            key[index] = value;
        }
        return key;
    }
}
