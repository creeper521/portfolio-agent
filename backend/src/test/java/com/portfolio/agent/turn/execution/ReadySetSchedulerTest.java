package com.portfolio.agent.turn.execution;

import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.SemanticTurnPlan;
import com.portfolio.agent.turn.planning.TaskDependency;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReadySetSchedulerTest {
    private final ReadySetScheduler scheduler = new ReadySetScheduler();

    @Test
    void downstreamBecomesReadyOnlyAfterInboundTerminalAndReceivesOnlyProducedSemanticResults() {
        SemanticTask upstream = ExecutionTestPlanFactory.generalTask("task-upstream", "upstream");
        SemanticTask downstream = ExecutionTestPlanFactory.generalTask("task-downstream", "downstream");
        SemanticTurnPlan plan = new SemanticTurnPlan(
                "public-1", List.of(), List.of(upstream, downstream),
                List.of(new TaskDependency(upstream.getTaskId(), downstream.getTaskId())));
        Map<String, TaskOutcome> outcomes = new LinkedHashMap<>();

        assertThat(scheduler.ready(plan, outcomes)).containsExactly(upstream);
        TaskArtifact artifact = ExecutionTestPlanFactory.artifact();
        outcomes.put(upstream.getTaskId(), new TaskOutcome(upstream.getTaskId(),
                new TaskOutcome.Produced(artifact, TaskOutcome.Fulfillment.FULL)));

        assertThat(scheduler.ready(plan, outcomes)).containsExactly(downstream);
        assertThat(scheduler.dependencyResults(plan, downstream.getTaskId(), outcomes))
                .containsExactly(artifact.getSemanticResult());
        assertThat(scheduler.blockedByDependencies(plan, downstream.getTaskId(), outcomes)).isFalse();
    }

    @Test
    void downstreamIsBlockedWhenAllInboundTasksFinishWithoutProducedResults() {
        SemanticTask upstream = ExecutionTestPlanFactory.generalTask("task-upstream", "upstream");
        SemanticTask downstream = ExecutionTestPlanFactory.generalTask("task-downstream", "downstream");
        SemanticTurnPlan plan = new SemanticTurnPlan(
                "public-1", List.of(), List.of(upstream, downstream),
                List.of(new TaskDependency(upstream.getTaskId(), downstream.getTaskId())));
        Map<String, TaskOutcome> outcomes = Map.of(upstream.getTaskId(),
                new TaskOutcome(upstream.getTaskId(),
                        new TaskOutcome.NoResult(TaskTerminalReason.NO_SUPPORTED_RESULT)));

        assertThat(scheduler.ready(plan, outcomes)).containsExactly(downstream);
        assertThat(scheduler.dependencyResults(plan, downstream.getTaskId(), outcomes)).isEmpty();
        assertThat(scheduler.blockedByDependencies(plan, downstream.getTaskId(), outcomes)).isTrue();
    }
}
