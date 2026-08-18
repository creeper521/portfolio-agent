package com.portfolio.agent.turn.execution;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskOutcomeContractTest {
    @Test
    void terminalIsAClosedSingleAxisAndOnlyProducedCarriesAnArtifact() {
        TaskArtifact artifact = new TaskArtifact(
                new Result(), new Presentation(), TaskProvenance.none());
        TaskOutcome produced = new TaskOutcome(
                "task-1", new TaskOutcome.Produced(artifact, TaskOutcome.Fulfillment.PARTIAL));
        TaskOutcome failed = new TaskOutcome(
                "task-2", new TaskOutcome.Failed(TaskTerminalReason.EXECUTION_FAILED));

        assertThat(TaskOutcome.Terminal.class.isSealed()).isTrue();
        assertThat(produced.getProducedArtifact()).containsSame(artifact);
        assertThat(failed.getProducedArtifact()).isEmpty();
        assertThat(((TaskOutcome.ReasonTerminal) failed.getTerminal()).getReason())
                .isEqualTo(TaskTerminalReason.EXECUTION_FAILED);
    }

    private static final class Result implements TaskSemanticResult { }
    private static final class Presentation implements TaskPresentation { }
}
