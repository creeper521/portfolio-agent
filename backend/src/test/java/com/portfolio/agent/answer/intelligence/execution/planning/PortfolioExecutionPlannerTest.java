package com.portfolio.agent.answer.intelligence.execution.planning;

import com.portfolio.agent.answer.routing.domain.AuthorizedContextReference;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTaskExecutionContext;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.domain.TaskExecutionAllowance;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PortfolioExecutionPlannerTest {

    private static final Instant DEADLINE = Instant.parse("2027-08-12T04:00:00Z");

    @Test
    void sameTypedTaskProducesSameSingleInvocation() {
        SubjectReference subject = SubjectReference.project("project-a", "public-v1");
        SemanticTask task = SemanticTask.create(
                "task-1", SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT,
                SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO, "ignored goal label",
                new SemanticTaskParameters.PortfolioFact(
                        subject, Set.of("OVERVIEW"), "INTERVIEWER"),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                com.portfolio.agent.answer.routing.domain.TaskConfidence.highRule(), List.of(subject));
        SemanticTaskExecutionContext context = new SemanticTaskExecutionContext(
                task, List.of(), List.of(), "public-v1",
                TaskExecutionAllowance.portfolio(DEADLINE), List.of());
        PortfolioExecutionPlanner planner = new PortfolioExecutionPlanner(
                new PortfolioCapabilityCatalog());

        PortfolioExecutionPlan first = planner.plan(context);
        PortfolioExecutionPlan second = planner.plan(context);

        assertEquals(first, second);
        assertEquals(1, first.getInvocations().size());
        assertEquals("PORTFOLIO_EVIDENCE_RETRIEVAL_V1",
                first.getInvocations().getFirst().getCapabilityId());
    }

    @Test
    void generalTaskCannotEnterPortfolioPlanner() {
        SemanticTask task = SemanticTask.create(
                "task-1", SemanticRoutingTypes.SemanticTaskType.GENERAL_EXPLANATION,
                SemanticRoutingTypes.TaskSourceDomain.GENERAL, "explain",
                new SemanticTaskParameters.GeneralExplanation(
                        "topic", "STANDARD", "GUEST"),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                com.portfolio.agent.answer.routing.domain.TaskConfidence.highRule(), List.of());
        SemanticTaskExecutionContext context = new SemanticTaskExecutionContext(
                task, List.of(), List.of(), "public-v1",
                TaskExecutionAllowance.none(DEADLINE), List.of());

        assertThrows(IllegalArgumentException.class, () -> new PortfolioExecutionPlanner(
                new PortfolioCapabilityCatalog()).plan(context));
    }
}
