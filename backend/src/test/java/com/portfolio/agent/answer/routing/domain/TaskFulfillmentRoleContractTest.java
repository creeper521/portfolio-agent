package com.portfolio.agent.answer.routing.domain;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.RequestedOutput;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SemanticTaskType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskSourceDomain;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TaskFulfillmentRoleContractTest {

    @Test
    void roleIsExplicitAndIsNotInferredFromTaskType() {
        SemanticTaskParameters.GeneralExplanation parameters =
                new SemanticTaskParameters.GeneralExplanation("topic", "STANDARD", "GUEST");

        SemanticTask supporting = SemanticTask.create(
                "task-01", SemanticTaskType.GENERAL_EXPLANATION, TaskSourceDomain.GENERAL,
                "general", parameters, Set.of(RequestedOutput.SUMMARY), TaskConfidence.highRule(),
                List.of(), TaskFulfillmentRole.SUPPORTING);

        assertThat(supporting.getFulfillmentRole()).isEqualTo(TaskFulfillmentRole.SUPPORTING);
    }

    @Test
    void planFingerprintChangesWhenOnlyTheFulfillmentRoleChanges() {
        SemanticTaskParameters.GeneralExplanation parameters =
                new SemanticTaskParameters.GeneralExplanation("topic", "STANDARD", "GUEST");
        SemanticTask primary = SemanticTask.create(
                "task-01", SemanticTaskType.GENERAL_EXPLANATION, TaskSourceDomain.GENERAL,
                "general", parameters, Set.of(RequestedOutput.SUMMARY), TaskConfidence.highRule(),
                List.of(), TaskFulfillmentRole.PRIMARY);
        SemanticTask optional = SemanticTask.create(
                "task-01", SemanticTaskType.GENERAL_EXPLANATION, TaskSourceDomain.GENERAL,
                "general", parameters, Set.of(RequestedOutput.SUMMARY), TaskConfidence.highRule(),
                List.of(), TaskFulfillmentRole.OPTIONAL);

        assertThat(primary).isNotEqualTo(optional);
        assertThat(primary.getFulfillmentRole()).isNotEqualTo(optional.getFulfillmentRole());
    }
}
