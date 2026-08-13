package com.portfolio.agent.answer.intelligence.execution.planning;

import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTaskExecutionContext;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.domain.TaskConfidence;
import com.portfolio.agent.answer.routing.domain.TaskExecutionAllowance;

import java.time.Instant;
import java.util.List;
import java.util.Set;

final class PortfolioPlannerFixtures {

    private PortfolioPlannerFixtures() {
    }

    static SemanticTaskExecutionContext factContext() {
        SubjectReference subject = SubjectReference.project("project-a", "public-v1");
        SemanticTask task = SemanticTask.create(
                "task-1", SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT,
                SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO, "overview",
                new SemanticTaskParameters.PortfolioFact(subject, Set.of("OVERVIEW"), "GUEST"),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                TaskConfidence.highRule(), List.of(subject));
        return new SemanticTaskExecutionContext(
                task, List.of(), List.of(), "public-v1",
                TaskExecutionAllowance.portfolio(Instant.parse("2027-08-12T04:00:00Z")), List.of());
    }
}
