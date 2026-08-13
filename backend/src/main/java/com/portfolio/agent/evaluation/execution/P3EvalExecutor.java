package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.answer.routing.adapter.execution.P3PortfolioSemanticTaskExecutor;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTaskExecutionContext;
import com.portfolio.agent.answer.routing.domain.TaskExecutionAllowance;
import com.portfolio.agent.answer.routing.domain.TaskOutcome;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Eval adapter that consumes exactly the same typed P3 seam as production. */
public final class P3EvalExecutor implements EvalExecutor {

    private final P3PortfolioSemanticTaskExecutor executor;
    private final RuntimeContentSnapshot bundle;

    public P3EvalExecutor(
            P3PortfolioSemanticTaskExecutor executor,
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
            SemanticTaskExecutionContext executionContext = new SemanticTaskExecutionContext(
                    task,
                    List.of(),
                    List.of(),
                    runContext.getContentVersion(),
                    TaskExecutionAllowance.forTask(task.getTaskType(),
                            TaskExecutionAllowance.PORTFOLIO_CHARACTER_LIMIT, deadline),
                    List.of());
            TaskOutcome outcome = executor.execute(executionContext);
            return map(input, task, outcome);
        } catch (RuntimeException failure) {
            return error(input, "EXECUTOR_ERROR");
        }
    }

    private EvalObservation map(
            EvalExecutionInput input,
            SemanticTask task,
            TaskOutcome outcome) {
        List<String> reasonCodes = new ArrayList<>(outcome.getReasonCodes());
        if (reasonCodes.isEmpty()) {
            reasonCodes.add(outcome.getResolution().name());
        }
        return new EvalObservation(
                input.getCaseId(), input.getLayer(), input.getTrialIndex(),
                isPass(outcome) ? EvalObservationStatus.PASS : EvalObservationStatus.FAIL,
                subjectSlug(task, SubjectType.PROJECT), subjectSlug(task, SubjectType.CASE),
                List.of(), List.of(), List.of(),
                resolution(outcome.getResolution()), ConversationAnswerScope.PORTFOLIO,
                GenerationMode.DETERMINISTIC, AnswerSource.RETRIEVAL,
                List.copyOf(reasonCodes), 0L, EvalProviderUsage.unavailable(),
                EvalAnswerShape.empty(), outcome.isDegraded(), false);
    }

    private boolean isPass(TaskOutcome outcome) {
        return outcome.getExecutionStatus() == TaskOutcome.TaskExecutionStatus.SUCCEEDED
                && (outcome.getResolution() == TaskOutcome.TaskResolution.ANSWERED
                || outcome.getResolution() == TaskOutcome.TaskResolution.PARTIALLY_ANSWERED);
    }

    private AnswerResolution resolution(TaskOutcome.TaskResolution resolution) {
        return switch (resolution) {
            case ANSWERED, PARTIALLY_ANSWERED -> AnswerResolution.ANSWERED;
            case BOUNDARY -> AnswerResolution.BOUNDARY;
            case CAPABILITY_UNAVAILABLE, DEPENDENCY_UNAVAILABLE,
                    NOT_EXECUTED_BUDGET -> AnswerResolution.CAPABILITY_UNAVAILABLE;
            case REJECTED -> AnswerResolution.REJECTED;
            case NOT_SUPPORTED, EMPTY, PRESENTATION_BLOCKED -> AnswerResolution.NOT_SUPPORTED;
            case NOT_APPLICABLE -> AnswerResolution.INVALID_INPUT;
        };
    }

    private String subjectSlug(SemanticTask task, SubjectType type) {
        return task.getSubjectReferences().stream()
                .filter(subject -> subject.getSubjectType() == type)
                .map(subject -> slug(type, subject.getSubjectId()))
                .findFirst()
                .orElse(null);
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
