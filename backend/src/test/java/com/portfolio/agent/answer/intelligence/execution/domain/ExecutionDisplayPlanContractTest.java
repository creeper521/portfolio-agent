package com.portfolio.agent.answer.intelligence.execution.domain;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SemanticTaskType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExecutionDisplayPlanContractTest {

    @Test
    void finalSnapshotContainsExactlyFourSafeStages() {
        List<ExecutionDisplayPlan.Stage> stages = List.of(
                new ExecutionDisplayPlan.Stage(
                        ExecutionDisplayPlan.StageCode.SCOPE_CONFIRMED,
                        "确认查询范围", ExecutionDisplayPlan.StageStatus.COMPLETED),
                new ExecutionDisplayPlan.Stage(
                        ExecutionDisplayPlan.StageCode.MATERIALS_RETRIEVED,
                        "查找已发布材料", ExecutionDisplayPlan.StageStatus.COMPLETED),
                new ExecutionDisplayPlan.Stage(
                        ExecutionDisplayPlan.StageCode.EVIDENCE_VALIDATED,
                        "核验证据", ExecutionDisplayPlan.StageStatus.COMPLETED),
                new ExecutionDisplayPlan.Stage(
                        ExecutionDisplayPlan.StageCode.RESULT_COMPOSED,
                        "形成回答", ExecutionDisplayPlan.StageStatus.COMPLETED));
        ExecutionDisplayPlan plan = new ExecutionDisplayPlan(
                ExecutionDisplayPlan.OverallStatus.COMPLETED,
                List.of(new ExecutionDisplayPlan.TaskDisplay(
                        0, SemanticTaskType.PORTFOLIO_FACT,
                        ExecutionDisplayPlan.TaskDisplayStatus.COMPLETED, stages)));

        assertEquals("p3-display-v1", plan.getContractVersion());
        assertEquals(ExecutionDisplayPlan.SnapshotType.FINAL, plan.getSnapshotType());
        assertEquals(4, plan.getTasks().getFirst().getStages().size());
    }
}
