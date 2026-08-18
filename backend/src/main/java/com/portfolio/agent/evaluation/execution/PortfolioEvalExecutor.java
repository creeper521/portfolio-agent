package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.turn.capability.portfolio.PortfolioTaskExecutor;
import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.GoalSubjectReference;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType;
import com.portfolio.agent.evaluation.domain.EvalAnswerShape;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalObservation;
import com.portfolio.agent.evaluation.domain.EvalObservationStatus;
import com.portfolio.agent.evaluation.domain.EvalProviderUsage;
import com.portfolio.agent.portfolio.domain.CaseStudy;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Eval adapter that consumes exactly the same typed Portfolio seam as production. */
public final class PortfolioEvalExecutor implements EvalExecutor {

    private final PortfolioTaskExecutor executor;
    private final RuntimeContentSnapshot bundle;

    public PortfolioEvalExecutor(
            PortfolioTaskExecutor executor,
            RuntimeContentSnapshot bundle) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.bundle = Objects.requireNonNull(bundle, "bundle");
    }

    @Override
    public boolean supports(EvalLayer layer) {
        return layer == EvalLayer.INTELLIGENCE;
    }

    @Override
    public EvalObservation execute(EvalExecutionInput input, EvalRunContext runContext) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(runContext, "runContext");
        SemanticTask task = input.getSemanticTask().orElse(null);
        if (task == null) {
            return error(input, "MISSING_TYPED_TASK");
        }
        if (!bundle.getContentVersion().equals(runContext.getContentVersion())) {
            return error(input, "CONTENT_VERSION_UNAVAILABLE");
        }
        try {
            Instant deadline = Instant.now().plusSeconds(10);
            com.portfolio.agent.turn.execution.TaskExecutionContext executionContext =
                    new com.portfolio.agent.turn.execution.TaskExecutionContext(
                            task, List.of(), runContext.getContentVersion(),
                            new com.portfolio.agent.turn.execution.TurnDeadline(
                                    deadline, java.time.Clock.systemUTC()),
                            new com.portfolio.agent.turn.execution.CancellationSignal(),
                            false, false);
            executor.execute(executionContext);
            return pass(input, task);
        } catch (com.portfolio.agent.turn.execution.TaskTerminalException terminal) {
            return failed(input, task, terminal.getReason().name());
        } catch (RuntimeException failure) {
            return error(input, "EXECUTOR_ERROR");
        }
    }

    private EvalObservation failed(
            EvalExecutionInput input, SemanticTask task, String reasonCode) {
        return new EvalObservation(
                input.getCaseId(), input.getLayer(), input.getTrialIndex(),
                EvalObservationStatus.FAIL,
                subjectSlug(task, SubjectType.PROJECT), subjectSlug(task, SubjectType.CASE),
                List.of(), List.of(), List.of(),
                AnswerResolution.CAPABILITY_UNAVAILABLE, ConversationAnswerScope.PORTFOLIO,
                GenerationMode.DETERMINISTIC, AnswerSource.RETRIEVAL,
                List.of(reasonCode), 0L, EvalProviderUsage.unavailable(),
                EvalAnswerShape.empty(), false, false);
    }

    private EvalObservation pass(EvalExecutionInput input, SemanticTask task) {
        return new EvalObservation(
                input.getCaseId(), input.getLayer(), input.getTrialIndex(),
                EvalObservationStatus.PASS,
                subjectSlug(task, SubjectType.PROJECT), subjectSlug(task, SubjectType.CASE),
                List.of(), List.of(), List.of(),
                AnswerResolution.ANSWERED, ConversationAnswerScope.PORTFOLIO,
                GenerationMode.DETERMINISTIC, AnswerSource.RETRIEVAL,
                List.of(), 0L, EvalProviderUsage.unavailable(),
                EvalAnswerShape.empty(), false, false);
    }

    private String subjectSlug(SemanticTask task, SubjectType type) {
        return task.getSubjectReferences().stream()
                .filter(subject -> matches(subject.getKind(), type))
                .map(subject -> slug(type, subject.getReference()))
                .findFirst()
                .orElse(null);
    }

    private boolean matches(GoalSubjectReference.Kind kind, SubjectType type) {
        return (kind == GoalSubjectReference.Kind.PROJECT && type == SubjectType.PROJECT)
                || (kind == GoalSubjectReference.Kind.CASE && type == SubjectType.CASE)
                || (kind == GoalSubjectReference.Kind.RESULT && type == SubjectType.RESULT);
    }

    private String slug(SubjectType type, String subjectId) {
        if (type == SubjectType.PROJECT) {
            return bundle.getProjects().stream()
                    .filter(project -> project.getId().equals(subjectId))
                    .map(ProjectProfile::getSlug)
                    .findFirst().orElse(subjectId);
        }
        return bundle.getCases().stream()
                .filter(caseStudy -> caseStudy.getId().equals(subjectId))
                .map(CaseStudy::getSlug)
                .findFirst().orElse(subjectId);
    }

    private EvalObservation error(EvalExecutionInput input, String reasonCode) {
        return new EvalObservation(
                input.getCaseId(), input.getLayer(), input.getTrialIndex(),
                EvalObservationStatus.ERROR, null, null, List.of(), List.of(), List.of(),
                AnswerResolution.CAPABILITY_UNAVAILABLE, ConversationAnswerScope.PORTFOLIO,
                GenerationMode.DETERMINISTIC, AnswerSource.RETRIEVAL,
                List.of(reasonCode), 0L, EvalProviderUsage.unavailable(),
                EvalAnswerShape.empty(), false, false);
    }
}
