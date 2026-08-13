package com.portfolio.agent.answer.routing.domain;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SemanticTaskType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskSourceDomain;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticTaskExecutionContextTest {

    @Test
    void portfolioAllowanceUsesFrozenP3Limits() {
        TaskExecutionAllowance allowance = TaskExecutionAllowance.portfolio(
                Instant.parse("2026-08-12T04:00:00Z"));

        assertEquals(1, allowance.getLogicalRetrievalLimit());
        assertEquals(2, allowance.getBackendAttemptLimit());
        assertEquals(128, allowance.getEvidenceUnitLimit());
        assertEquals(96, allowance.getPublicReferenceLimit());
        assertEquals(4000, allowance.getCharacterLimit());
        assertTrue(allowance.getAbsoluteDeadline().isAfter(Instant.now().minusSeconds(1))
                || allowance.getAbsoluteDeadline().equals(Instant.parse("2026-08-12T04:00:00Z")));
    }

    @Test
    void executionContextKeepsOnlyTypedExecutionInputs() {
        SubjectReference subject = SubjectReference.project("project-a", "public-v1");
        SemanticTask task = SemanticTask.create(
                "task-1", SemanticTaskType.PORTFOLIO_FACT, TaskSourceDomain.PORTFOLIO,
                "overview", new SemanticTaskParameters.PortfolioFact(
                        subject, Set.of("OVERVIEW"), "INTERVIEWER"),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                TaskConfidence.highRule(), List.of(subject));
        SemanticTaskExecutionContext context = new SemanticTaskExecutionContext(
                task, List.of(), List.of(), "public-v1",
                TaskExecutionAllowance.portfolio(Instant.parse("2026-08-12T04:00:00Z")),
                List.of(new AuthorizedContextReference("handle-1", "RECOMMENDATION")));

        assertEquals(task, context.getSemanticTask());
        assertEquals("public-v1", context.getExpectedContentVersion());
        assertEquals(1, context.getAuthorizedContextReferences().size());
        assertTrue(context.toString().contains("taskType=PORTFOLIO_FACT"));
    }

    @Test
    void turnCharacterBudgetIsStableAndDoesNotBorrowUnusedCapacity() {
        Instant started = Instant.parse("2026-08-12T03:00:00Z");
        SemanticTurnExecutionBudget budget = SemanticTurnExecutionBudget.forExecutableTaskCount(
                6, started, started.plusSeconds(10));

        assertEquals(List.of(1334, 1334, 1333, 1333, 1333, 1333),
                budget.getAllowancesByTaskId().values().stream()
                        .map(TaskExecutionAllowance::getCharacterLimit)
                        .collect(Collectors.toList()));
    }
}
