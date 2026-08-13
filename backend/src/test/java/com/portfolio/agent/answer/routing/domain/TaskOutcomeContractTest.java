package com.portfolio.agent.answer.routing.domain;

import com.portfolio.agent.answer.domain.GroundedAnswerContribution;
import com.portfolio.agent.answer.intelligence.execution.domain.SafeReasonCode;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskSourceDomain;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskOutcomeContractTest {

    @Test
    void typedPayloadsAreImmutableValues() {
        List<String> blocks = new ArrayList<>(List.of("A verified section"));
        TaskResultPayload.SectionResultPayload payload =
                new TaskResultPayload.SectionResultPayload(blocks, "A concise summary");

        blocks.clear();

        assertEquals(List.of("A verified section"), payload.getBlocks());
        assertThrows(UnsupportedOperationException.class, () -> payload.getBlocks().clear());
        assertEquals(payload, new TaskResultPayload.SectionResultPayload(
                List.of("A verified section"), "A concise summary"));
    }

    @Test
    void onlyAnsweredSuccessMayCarryRenderablePayload() {
        TaskResultPayload payload = new TaskResultPayload.SectionResultPayload(
                List.of("A verified section"), null);

        assertThrows(IllegalArgumentException.class, () -> TaskOutcome.create(
                "task-01",
                TaskOutcome.TaskExecutionStatus.FAILED,
                TaskOutcome.TaskResolution.NOT_APPLICABLE,
                TaskOutcome.TaskEvidenceState.NOT_APPLICABLE,
                false,
                Set.of("EXECUTION_PROVIDER_FAILURE"),
                null,
                TaskSourceDomain.PORTFOLIO,
                TaskResultProvenance.direct(TaskSourceDomain.PORTFOLIO, List.of(), List.of()),
                payload));
    }

    @Test
    void evidenceInsufficientOutcomeCannotCarryRenderablePayload() {
        TaskResultPayload payload = new TaskResultPayload.SectionResultPayload(
                List.of("A section"), null);

        assertThrows(IllegalArgumentException.class, () -> TaskOutcome.create(
                "task-01",
                TaskOutcome.TaskExecutionStatus.SUCCEEDED,
                TaskOutcome.TaskResolution.ANSWERED,
                TaskOutcome.TaskEvidenceState.INSUFFICIENT,
                false,
                Set.of(),
                null,
                TaskSourceDomain.PORTFOLIO,
                TaskResultProvenance.direct(TaskSourceDomain.PORTFOLIO, List.of(), List.of()),
                payload));
    }

    @Test
    void blockedAndFailedOutcomesNeverExposeRenderablePayload() {
        TaskOutcome blocked = TaskOutcome.blocked(
                "task-02", TaskSourceDomain.SYNTHESIS, "EXECUTION_DEPENDENCY_BLOCKED");
        TaskOutcome failed = TaskOutcome.failed(
                "task-03", TaskSourceDomain.PORTFOLIO, "EXECUTION_PROVIDER_FAILURE");

        assertTrue(blocked.getResultPayload().isEmpty());
        assertTrue(failed.getResultPayload().isEmpty());
        assertEquals(TaskOutcome.TaskEvidenceState.NOT_APPLICABLE, blocked.getEvidenceState());
        assertEquals(TaskOutcome.TaskResolution.NOT_APPLICABLE, failed.getResolution());
    }

    @Test
    void degradedIsIndependentFromExecutionResolutionAndEvidenceState() {
        TaskOutcome outcome = TaskOutcome.notSupported(
                "task-04",
                TaskSourceDomain.PORTFOLIO,
                true,
                "EVIDENCE_INSUFFICIENT");

        assertEquals(TaskOutcome.TaskExecutionStatus.SUCCEEDED, outcome.getExecutionStatus());
        assertEquals(TaskOutcome.TaskResolution.NOT_SUPPORTED, outcome.getResolution());
        assertEquals(TaskOutcome.TaskEvidenceState.INSUFFICIENT, outcome.getEvidenceState());
        assertTrue(outcome.isDegraded());
        assertFalse(outcome.getResultPayload().isPresent());
    }

    @Test
    void groundedContributionIsTheOnlyNewRenderableMaterialShape() {
        GroundedAnswerContribution contribution = new GroundedAnswerContribution(
                List.of("A verified statement"), List.of("source-a"), List.of(), List.of());

        TaskOutcome outcome = TaskOutcome.answeredWithContribution(
                "task-01", TaskSourceDomain.PORTFOLIO,
                contribution,
                TaskResultProvenance.direct(TaskSourceDomain.PORTFOLIO, List.of(), List.of()),
                false);

        assertEquals(TaskOutcome.TaskResolution.ANSWERED, outcome.getResolution());
        assertEquals(contribution, outcome.getContribution().orElseThrow());
        assertTrue(outcome.hasRenderablePayload());
        assertEquals(10, SafeReasonCode.values().length);
    }

    @Test
    void synthesisProvenanceRequiresAtLeastTwoSourceTasksAndDirectProvenanceRejectsSynthesis() {
        assertThrows(IllegalArgumentException.class, () -> TaskResultProvenance.synthesized(
                Set.of(TaskSourceDomain.PORTFOLIO), List.of("task-01"), List.of("claim-01"), List.of()));
        assertThrows(IllegalArgumentException.class, () -> TaskResultProvenance.direct(
                TaskSourceDomain.SYNTHESIS, List.of(), List.of()));
    }
}
