package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.adapter.crypto.JdkPlanCryptographyAdapter;
import com.portfolio.agent.answer.domain.AgentTurnResult;
import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerConstructionMode;
import com.portfolio.agent.answer.domain.AnswerEvidenceState;
import com.portfolio.agent.answer.domain.ConversationAnswerResult;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.domain.RuntimeAnswerContent;
import com.portfolio.agent.answer.dto.request.ConversationAnswerRequest;
import com.portfolio.agent.answer.dto.request.PlanConfirmationRequest;
import com.portfolio.agent.answer.mapper.SemanticTurnRequestMapper;
import com.portfolio.agent.answer.routing.domain.ExecutionSelection;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTaskExecutionContext;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.SemanticTurnPlan;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.domain.TaskConfidence;
import com.portfolio.agent.answer.routing.domain.TaskComposition;
import com.portfolio.agent.answer.routing.domain.TaskOutcome;
import com.portfolio.agent.answer.routing.domain.TaskResultPayload;
import com.portfolio.agent.answer.routing.domain.TaskResultProvenance;
import com.portfolio.agent.answer.intelligence.domain.AnswerIntentSource;
import com.portfolio.agent.answer.composition.domain.CompositionMode;
import com.portfolio.agent.answer.service.ConversationalAgentRuntime;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationalAgentRuntimeTest {

    @Test
    void routesEachAskExactlyOnceWithoutLegacyRoutingDependencies() {
        CountingRouter router = new CountingRouter(SemanticTurnDecision.boundary(
                Set.of("ROUTING_GLOBAL_BOUNDARY")));
        Fixture fixture = fixture(router);

        ConversationAnswerResult result = fixture.runtime.answer(ask("private repository"));

        assertThat(router.callCount).isEqualTo(1);
        assertThat(result.getAgentTurn().getDisposition())
                .isEqualTo(AgentTurnResult.Disposition.BOUNDARY);
    }

    @Test
    void confirmationNeverRoutesOrExecutesWhenItsOpaqueEnvelopeIsInvalid() {
        CountingRouter router = new CountingRouter(SemanticTurnDecision.boundary(
                Set.of("ROUTING_GLOBAL_BOUNDARY")));
        Fixture fixture = fixture(router);

        ConversationAnswerResult result = fixture.runtime.answer(invalidConfirmation());

        assertThat(router.callCount).isZero();
        assertThat(result.getAgentTurn().getDisposition())
                .isEqualTo(AgentTurnResult.Disposition.PLAN_INVALIDATED);
        assertThat(result.getAgentTurn().getInvalidationReason()).hasValue(
                com.portfolio.agent.answer.routing.domain.PlanConfirmation.PlanInvalidationReason
                        .PLAN_INTEGRITY_INVALID);
    }

    @Test
    void semanticDiagnosticsContainOnlyApprovedSafeBuckets() {
        CountingRouter router = new CountingRouter(SemanticTurnDecision.boundary(
                Set.of("ROUTING_GLOBAL_BOUNDARY")));
        Fixture fixture = fixture(router);

        fixture.runtime.answer(ask("private repository"));

        assertThat(fixture.events).isNotEmpty();
        assertThat(fixture.events.get(0).getName()).isEqualTo("semantic.turn.completed");
        assertThat(fixture.events.get(0).getFields()).containsOnlyKeys(
                "plan.task.count",
                "plan.task.succeeded.count",
                "plan.task.blocked.count",
                "plan.task.failed.count",
                "plan.outcome",
                "plan.disposition");
        assertThat(fixture.events.stream().map(DiagnosticEvent::getFields)
                .flatMap(values -> values.keySet().stream()))
                .doesNotContain("question", "planId", "planFingerprint", "confirmationPlan",
                        "integrityToken", "taskId");
    }

    @Test
    void mapsAReadyPlanWithoutAnyAnsweredTaskToNotSupported() {
        SemanticTask task = portfolioFact();
        SemanticTurnPlan plan = new SemanticTurnPlan(
                "plan-no-result", "public-v1", SemanticTurnPlan.PlanSource.RULE,
                List.of(task), List.of(), List.of(),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                SemanticTurnPlan.PlanConfirmationPolicy.noConfirmation());
        ValidatedSemanticTurnPlan validated = new SemanticPlanValidator(new PlanFingerprintService())
                .validate(plan, "stp-v1").getValidatedPlan().orElseThrow();
        CountingRouter router = new CountingRouter(SemanticTurnDecision.ready(
                validated, ExecutionSelection.allExecutable(Set.of(task.getTaskId()))));

        ConversationAnswerResult result = fixture(router).runtime.answer(ask("review project"));

        assertThat(result.getAgentTurn().getOutcome().orElseThrow().getPlanOutcome())
                .isEqualTo(com.portfolio.agent.answer.routing.domain.SemanticTurnOutcome.PlanOutcome.NO_RESULT);
        assertThat(result.getResolution()).isEqualTo(AnswerResolution.NOT_SUPPORTED);
    }

    @Test
    void doesNotProjectLegacyFallbackWhenAReadyPortfolioPlanHasFailedExecution() {
        SemanticTask task = portfolioFact();
        SemanticTurnPlan plan = new SemanticTurnPlan(
                "plan-failed", "public-v1", SemanticTurnPlan.PlanSource.RULE,
                List.of(task), List.of(), List.of(),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                SemanticTurnPlan.PlanConfirmationPolicy.noConfirmation());
        ValidatedSemanticTurnPlan validated = new SemanticPlanValidator(new PlanFingerprintService())
                .validate(plan, "stp-v1").getValidatedPlan().orElseThrow();
        CountingRouter router = new CountingRouter(SemanticTurnDecision.ready(
                validated, ExecutionSelection.allExecutable(Set.of(task.getTaskId()))));
        SemanticTaskExecutor failedExecutor = new SemanticTaskExecutor() {
            @Override
            public SemanticRoutingTypes.TaskSourceDomain getSourceDomain() {
                return SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO;
            }

            @Override
            public TaskOutcome execute(SemanticTaskExecutionContext context) {
                return TaskOutcome.failed(task.getTaskId(),
                        SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                        "EVIDENCE_INTEGRITY_FAILURE");
            }
        };

        ConversationAnswerResult result = fixture(
                router, new SemanticTurnCoordinator(List.of(failedExecutor))).runtime.answer(
                ask("review project"));

        assertThat(result.getResolution()).isEqualTo(AnswerResolution.CAPABILITY_UNAVAILABLE);
        assertThat(result.getBlocks()).isEmpty();
        assertThat(result.getEvidenceState()).isEqualTo(AnswerEvidenceState.INSUFFICIENT);
        assertThat(result.getAgentTurn().getOutcome().orElseThrow().getPlanOutcome())
                .isEqualTo(com.portfolio.agent.answer.routing.domain.SemanticTurnOutcome.PlanOutcome.FAILED);
    }

    @Test
    void projectsACompletedGeneralTaskAsModelGenerationWithoutPortfolioEvidence() {
        SemanticTask task = SemanticTask.create(
                "task-general", SemanticRoutingTypes.SemanticTaskType.GENERAL_EXPLANATION,
                SemanticRoutingTypes.TaskSourceDomain.GENERAL, "explain optimistic locking",
                new SemanticTaskParameters.GeneralExplanation(
                        "Explain optimistic locking", "STANDARD", "INTERVIEWER"),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                TaskConfidence.highRule(), List.of());
        SemanticTurnPlan plan = new SemanticTurnPlan(
                "plan-general", "public-v1", SemanticTurnPlan.PlanSource.RULE,
                List.of(task), List.of(), List.of(),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                SemanticTurnPlan.PlanConfirmationPolicy.noConfirmation());
        ValidatedSemanticTurnPlan validated = new SemanticPlanValidator(new PlanFingerprintService())
                .validate(plan, "stp-v1").getValidatedPlan().orElseThrow();
        CountingRouter router = new CountingRouter(SemanticTurnDecision.ready(
                validated, ExecutionSelection.allExecutable(Set.of(task.getTaskId()))));
        SemanticTaskExecutor executor = new SemanticTaskExecutor() {
            @Override
            public SemanticRoutingTypes.TaskSourceDomain getSourceDomain() {
                return SemanticRoutingTypes.TaskSourceDomain.GENERAL;
            }

            @Override
            public TaskOutcome execute(SemanticTaskExecutionContext context) {
                return TaskOutcome.answered(
                        task.getTaskId(), SemanticRoutingTypes.TaskSourceDomain.GENERAL,
                        new TaskResultPayload.SectionResultPayload(
                                List.of("Optimistic locking checks a version before update."), null),
                        TaskResultProvenance.direct(
                                SemanticRoutingTypes.TaskSourceDomain.GENERAL, List.of(), List.of()),
                        false);
            }
        };

        ConversationAnswerResult result = fixture(
                router, new SemanticTurnCoordinator(List.of(executor))).runtime.answer(
                        ask("Explain optimistic locking"));

        assertThat(result.getResolution()).isEqualTo(AnswerResolution.ANSWERED);
        assertThat(result.getGenerationMode()).isEqualTo(GenerationMode.MODEL);
        assertThat(result.getConstructionMode()).isEqualTo(AnswerConstructionMode.GENERAL_MODEL);
        assertThat(result.getIntentSource()).isEqualTo(AnswerIntentSource.RULE);
        assertThat(result.getEvidenceState()).isEqualTo(AnswerEvidenceState.NOT_REQUIRED);
    }

    @Test
    void projectsGeneralAndPortfolioPayloadsAsMixedComposition() {
        SemanticTask general = generalTask("task-general");
        SemanticTask portfolio = portfolioFact("task-portfolio", "project-a");
        ConversationAnswerResult result = answerMultiTaskPlan(
                List.of(general, portfolio),
                List.of(generalExecutor(general), portfolioExecutor(CompositionMode.DETERMINISTIC)));

        assertThat(result.getGenerationMode()).isEqualTo(GenerationMode.MIXED);
        assertThat(result.getConstructionMode()).isEqualTo(
                AnswerConstructionMode.MIXED_COMPOSITION);
    }

    @Test
    void projectsAllFallbackPortfolioPayloadsAsFallbackRatherThanMixed() {
        SemanticTask first = portfolioFact("task-first", "project-a");
        SemanticTask second = portfolioFact("task-second", "project-b");
        ConversationAnswerResult result = answerMultiTaskPlan(
                List.of(first, second), List.of(portfolioExecutor(CompositionMode.FALLBACK)));

        assertThat(result.getGenerationMode()).isEqualTo(GenerationMode.FALLBACK);
        assertThat(result.getConstructionMode()).isEqualTo(
                AnswerConstructionMode.EVIDENCE_COMPOSITION);
    }

    @Test
    void projectsDeterministicAndFallbackPortfolioPayloadsAsMixed() {
        SemanticTask deterministic = portfolioFact("task-deterministic", "project-a");
        SemanticTask fallback = portfolioFact("task-fallback", "project-b");
        SemanticTaskExecutor executor = new SemanticTaskExecutor() {
            @Override
            public SemanticRoutingTypes.TaskSourceDomain getSourceDomain() {
                return SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO;
            }

            @Override
            public TaskOutcome execute(SemanticTaskExecutionContext context) {
                CompositionMode mode = context.getSemanticTask().getTaskId().equals("task-fallback")
                        ? CompositionMode.FALLBACK : CompositionMode.DETERMINISTIC;
                return portfolioOutcome(context.getSemanticTask(), mode);
            }
        };
        ConversationAnswerResult result = answerMultiTaskPlan(
                List.of(deterministic, fallback), List.of(executor));

        assertThat(result.getGenerationMode()).isEqualTo(GenerationMode.MIXED);
        assertThat(result.getConstructionMode()).isEqualTo(
                AnswerConstructionMode.MIXED_COMPOSITION);
    }

    private ConversationAnswerResult answerMultiTaskPlan(
            List<SemanticTask> tasks, List<SemanticTaskExecutor> executors) {
        SemanticTurnPlan plan = new SemanticTurnPlan(
                "plan-mixed", "public-v1", SemanticTurnPlan.PlanSource.RULE,
                tasks, List.of(), List.of(),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                SemanticTurnPlan.PlanConfirmationPolicy.noConfirmation());
        ValidatedSemanticTurnPlan validated = new SemanticPlanValidator(new PlanFingerprintService())
                .validate(plan, "stp-v1").getValidatedPlan().orElseThrow();
        CountingRouter router = new CountingRouter(SemanticTurnDecision.ready(
                validated, ExecutionSelection.allExecutable(tasks.stream()
                        .map(SemanticTask::getTaskId).collect(java.util.stream.Collectors.toSet()))));
        return fixture(router, new SemanticTurnCoordinator(executors)).runtime.answer(
                ask("compare public results"));
    }

    private SemanticTaskExecutor generalExecutor(SemanticTask task) {
        return new SemanticTaskExecutor() {
            @Override
            public SemanticRoutingTypes.TaskSourceDomain getSourceDomain() {
                return SemanticRoutingTypes.TaskSourceDomain.GENERAL;
            }

            @Override
            public TaskOutcome execute(SemanticTaskExecutionContext context) {
                return TaskOutcome.answered(task.getTaskId(), getSourceDomain(),
                        new TaskResultPayload.SectionResultPayload(
                                List.of("Public general explanation."), null),
                        TaskResultProvenance.direct(getSourceDomain(), List.of(), List.of()), false);
            }
        };
    }

    private SemanticTaskExecutor portfolioExecutor(CompositionMode mode) {
        return new SemanticTaskExecutor() {
            @Override
            public SemanticRoutingTypes.TaskSourceDomain getSourceDomain() {
                return SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO;
            }

            @Override
            public TaskOutcome execute(SemanticTaskExecutionContext context) {
                return portfolioOutcome(context.getSemanticTask(), mode);
            }
        };
    }

    private TaskOutcome portfolioOutcome(SemanticTask task, CompositionMode mode) {
        return TaskOutcome.answered(task.getTaskId(),
                SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                new TaskResultPayload.SectionResultPayload(
                        List.of("Public portfolio result."), null),
                TaskResultProvenance.direct(SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO,
                        List.of(), List.of()), mode == CompositionMode.FALLBACK)
                .withComposition(new TaskComposition(mode, mode == CompositionMode.FALLBACK));
    }

    private static SemanticTask generalTask(String taskId) {
        return SemanticTask.create(
                taskId, SemanticRoutingTypes.SemanticTaskType.GENERAL_EXPLANATION,
                SemanticRoutingTypes.TaskSourceDomain.GENERAL, "general explanation",
                new SemanticTaskParameters.GeneralExplanation(
                        "General explanation", "STANDARD", "INTERVIEWER"),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                TaskConfidence.highRule(), List.of());
    }

    private static SemanticTask portfolioFact() {
        return portfolioFact("task-01", "project-a");
    }

    private static SemanticTask portfolioFact(String taskId, String subjectId) {
        SubjectReference subject = SubjectReference.project(subjectId, "public-v1");
        return SemanticTask.create(
                taskId, SemanticRoutingTypes.SemanticTaskType.PORTFOLIO_FACT,
                SemanticRoutingTypes.TaskSourceDomain.PORTFOLIO, "review project",
                new SemanticTaskParameters.PortfolioFact(
                        subject, Set.of("OVERVIEW"), "INTERVIEWER"),
                Set.of(SemanticRoutingTypes.RequestedOutput.SUMMARY),
                TaskConfidence.highRule(), List.of(subject));
    }

    private Fixture fixture(TurnRouter router) {
        return fixture(router, new SemanticTurnCoordinator(List.of()));
    }

    private Fixture fixture(TurnRouter router, SemanticTurnCoordinator coordinator) {
        List<DiagnosticEvent> events = new ArrayList<>();
        DiagnosticEventPublisher diagnostics = events::add;
        SemanticPlanValidator validator = new SemanticPlanValidator(new PlanFingerprintService());
        PlanConfirmationService confirmations = new PlanConfirmationService(
                new JdkPlanCryptographyAdapter(new byte[32], new byte[32]), validator, Clock.systemUTC());
        ConversationalAgentRuntime runtime = new ConversationalAgentRuntime(
                () -> new RuntimeAnswerContent("public-v1", "bundle-v1", List.of()),
                new SemanticTurnRequestMapper(),
                router,
                confirmations,
                coordinator,
                decision -> { },
                diagnostics);
        return new Fixture(runtime, events);
    }

    private ConversationAnswerRequest ask(String question) {
        return new ConversationAnswerRequest(
                "turn-1", UUID.randomUUID(), null, null,
                ConversationAnswerRequest.TurnAction.ASK, question, List.of(), null,
                null, null, null, "stp-v1");
    }

    private ConversationAnswerRequest invalidConfirmation() {
        return new ConversationAnswerRequest(
                "turn-1", UUID.randomUUID(), null, null,
                ConversationAnswerRequest.TurnAction.CONFIRM_PLAN, null, List.of(), null,
                null, new PlanConfirmationRequest(
                        "confirm-invalid", "opaque-canonical-plan-envelope", "sha256:invalid", "invalid-token"),
                null, "stp-v1");
    }

    private static final class CountingRouter implements TurnRouter {

        private final SemanticTurnDecision result;
        private int callCount;

        private CountingRouter(SemanticTurnDecision result) {
            this.result = result;
        }

        @Override
        public SemanticTurnDecision route(
                com.portfolio.agent.answer.routing.domain.SemanticTurnInput input) {
            callCount++;
            return result;
        }
    }

    private static final class Fixture {

        private final ConversationalAgentRuntime runtime;
        private final List<DiagnosticEvent> events;

        private Fixture(ConversationalAgentRuntime runtime, List<DiagnosticEvent> events) {
            this.runtime = runtime;
            this.events = events;
        }
    }
}
