package com.portfolio.agent.answer.routing.adapter.execution;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.TaskFulfillmentRole;
import com.portfolio.agent.answer.routing.domain.TaskOutcome;
import com.portfolio.agent.answer.routing.domain.TaskResultPayload;
import com.portfolio.agent.answer.routing.domain.TaskResultProvenance;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicSynthesisTaskExecutorTest {

    @Test
    void productionExecutorPublishesOnlyPolicyAllowedRelation() {
        TaskOutcome result = new DeterministicSynthesisTaskExecutor(
                true,
                new com.portfolio.agent.answer.synthesis.service.CrossDomainRelationPolicy(),
                new com.portfolio.agent.answer.synthesis.service.DeterministicCrossDomainComposer())
                .execute(synthesisTask(), List.of(generalOutcome(), portfolioOutcome()));

        assertEquals(TaskOutcome.TaskResolution.ANSWERED, result.getResolution());
        TaskResultPayload.SynthesisResultPayload payload =
                (TaskResultPayload.SynthesisResultPayload) result.getResultPayload().orElseThrow();
        assertTrue(payload.getBlocks().getFirst().contains("ILLUSTRATES"));
        assertEquals(Set.of(SemanticRoutingTypes.TaskSourceDomain.GENERAL,
                        SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO),
                result.getProvenance().orElseThrow().getOriginDomains());
    }

    @Test
    void disabledPrimaryRelationFailsClosedWithoutSyntheticSynthesis() {
        TaskOutcome result = new DeterministicSynthesisTaskExecutor()
                .execute(synthesisTask(), List.of(generalOutcome(), portfolioOutcome()));

        assertEquals(TaskOutcome.TaskResolution.CAPABILITY_UNAVAILABLE, result.getResolution());
        assertTrue(result.getReasonCodes().contains("CROSS_DOMAIN_RELATION_DISABLED"));
        assertTrue(result.getResultPayload().isEmpty());
    }

    private SemanticTask synthesisTask() {
        return SemanticTask.create(
                "synthesis", SemanticRoutingTypes.SemanticTaskType.SYNTHESIS,
                SemanticRoutingTypes.TaskSourceDomain.SYNTHESIS, "relate",
                new SemanticTaskParameters.Synthesis(
                        List.of("general", "portfolio"), "RELATE", Set.of("IMPLEMENTATION")),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                com.portfolio.agent.answer.routing.domain.TaskConfidence.highRule(), List.of(),
                TaskFulfillmentRole.PRIMARY);
    }

    private TaskOutcome generalOutcome() {
        return TaskOutcome.answered(
                "general", SemanticRoutingTypes.TaskSourceDomain.GENERAL,
                new TaskResultPayload.SectionResultPayload(List.of("general implementation material"), null),
                TaskResultProvenance.direct(SemanticRoutingTypes.TaskSourceDomain.GENERAL,
                        List.of(), List.of()), false);
    }

    private TaskOutcome portfolioOutcome() {
        return TaskOutcome.answered(
                "portfolio", SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                new TaskResultPayload.SectionResultPayload(List.of("portfolio implementation material"), null),
                TaskResultProvenance.direct(SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                        List.of("claim-1"), List.of("evidence-1")), false);
    }
}
