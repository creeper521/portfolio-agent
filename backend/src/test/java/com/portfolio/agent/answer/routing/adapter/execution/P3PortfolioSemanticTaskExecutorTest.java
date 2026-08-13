package com.portfolio.agent.answer.routing.adapter.execution;

import com.portfolio.agent.answer.intelligence.execution.capability.CapabilityExecutionResult;
import com.portfolio.agent.answer.intelligence.execution.capability.PortfolioEvidenceCapability;
import com.portfolio.agent.answer.intelligence.execution.domain.SafeReasonCode;
import com.portfolio.agent.answer.intelligence.execution.planning.PortfolioCapabilityCatalog;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTaskExecutionContext;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.domain.TaskConfidence;
import com.portfolio.agent.answer.routing.domain.TaskExecutionAllowance;
import com.portfolio.agent.answer.routing.domain.TaskOutcome;
import com.portfolio.agent.answer.service.DeterministicPortfolioAnswerComposer;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class P3PortfolioSemanticTaskExecutorTest {

    @Test
    void executesOneBoundedCapabilityAndMapsUnavailableToSafeOutcome() {
        PortfolioEvidenceCapability capability = mock(PortfolioEvidenceCapability.class);
        when(capability.execute(any(), any())).thenReturn(
                CapabilityExecutionResult.unavailable(SafeReasonCode.CAPABILITY_TEMPORARILY_UNAVAILABLE));
        P3PortfolioSemanticTaskExecutor executor = new P3PortfolioSemanticTaskExecutor(
                new PortfolioCapabilityCatalog(), capability, new DeterministicPortfolioAnswerComposer());

        TaskOutcome outcome = executor.execute(context(task()));

        assertEquals(TaskOutcome.TaskExecutionStatus.SUCCEEDED, outcome.getExecutionStatus());
        assertEquals(TaskOutcome.TaskResolution.CAPABILITY_UNAVAILABLE, outcome.getResolution());
        assertEquals(Set.of("CAPABILITY_TEMPORARILY_UNAVAILABLE"), outcome.getReasonCodes());
        verify(capability).execute(any(), any());
    }

    @Test
    void rejectsNonPortfolioTaskBeforeCapabilityInvocation() {
        PortfolioEvidenceCapability capability = mock(PortfolioEvidenceCapability.class);
        P3PortfolioSemanticTaskExecutor executor = new P3PortfolioSemanticTaskExecutor(
                new PortfolioCapabilityCatalog(), capability, new DeterministicPortfolioAnswerComposer());
        SemanticTask general = SemanticTask.create(
                "task-general", SemanticRoutingTypes.SemanticTaskType.GENERAL_EXPLANATION,
                SemanticRoutingTypes.TaskSourceDomain.GENERAL, "general",
                new SemanticTaskParameters.GeneralExplanation("topic", "STANDARD", "GUEST"), Set.of(),
                TaskConfidence.highRule(), List.of());

        TaskOutcome outcome = executor.execute(new SemanticTaskExecutionContext(
                general, List.of(), List.of(), "public-v1",
                TaskExecutionAllowance.none(Instant.now().plusSeconds(10)), List.of()));

        assertEquals(TaskOutcome.TaskResolution.NOT_SUPPORTED, outcome.getResolution());
        org.mockito.Mockito.verifyNoInteractions(capability);
    }

    @Test
    void publishesOnlySafeFailureDiagnosticsWhenCapabilityThrows() {
        PortfolioEvidenceCapability capability = mock(PortfolioEvidenceCapability.class);
        when(capability.execute(any(), any())).thenThrow(new IllegalStateException(
                "visitor question and evidence body must never be logged"));
        List<DiagnosticEvent> events = new ArrayList<>();
        P3PortfolioSemanticTaskExecutor executor = new P3PortfolioSemanticTaskExecutor(
                new PortfolioCapabilityCatalog(), capability, new DeterministicPortfolioAnswerComposer(),
                events::add);

        TaskOutcome outcome = executor.execute(context(task()));

        assertEquals(TaskOutcome.TaskExecutionStatus.FAILED, outcome.getExecutionStatus());
        assertEquals(Set.of("PORTFOLIO_EXECUTION_FAILED"), outcome.getReasonCodes());
        assertEquals(1, events.size());
        DiagnosticEvent event = events.getFirst();
        assertEquals("portfolio.execution.failed", event.getName());
        assertEquals(Set.of("failure.stage", "failure.code", "capability.code", "task.type"),
                event.getFields().keySet());
        assertEquals("CAPABILITY", event.getFields().get("failure.stage"));
        assertEquals("PORTFOLIO_EXECUTION_FAILED", event.getFields().get("failure.code"));
        assertEquals(PortfolioCapabilityCatalog.CAPABILITY_ID,
                event.getFields().get("capability.code"));
        assertEquals("PORTFOLIO_FACT", event.getFields().get("task.type"));
        assertFalse(event.getFields().values().stream().anyMatch(
                value -> String.valueOf(value).contains("visitor question")));
    }

    private static SemanticTask task() {
        SubjectReference subject = SubjectReference.project("project-a", "public-v1");
        return SemanticTask.create(
                "task-portfolio", SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT,
                SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO, "describe project",
                new SemanticTaskParameters.PortfolioFact(subject, Set.of("IMPLEMENTATION"), "GUEST"),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY), TaskConfidence.highRule(),
                List.of(subject));
    }

    private static SemanticTaskExecutionContext context(SemanticTask task) {
        return new SemanticTaskExecutionContext(task, List.of(), List.of(), "public-v1",
                TaskExecutionAllowance.portfolio(Instant.now().plusSeconds(10)), List.of());
    }
}
