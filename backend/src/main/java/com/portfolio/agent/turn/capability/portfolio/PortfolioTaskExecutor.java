package com.portfolio.agent.turn.capability.portfolio;

import com.portfolio.agent.turn.capability.portfolio.evidence.ValidatedEvidenceBundle;
import com.portfolio.agent.turn.capability.portfolio.presentation.PortfolioPresentation;
import com.portfolio.agent.turn.capability.portfolio.presentation.PortfolioPresentationComposer;
import com.portfolio.agent.turn.capability.portfolio.presentation.PortfolioFactExpressionCompiler;
import com.portfolio.agent.turn.capability.portfolio.presentation.PortfolioFactExpressionPort;
import com.portfolio.agent.turn.capability.portfolio.semantic.PortfolioSemanticResult;
import com.portfolio.agent.turn.capability.portfolio.semantic.PortfolioSemanticResultFactory;
import com.portfolio.agent.turn.execution.SemanticTaskExecutor;
import com.portfolio.agent.turn.execution.TaskArtifact;
import com.portfolio.agent.turn.execution.TaskExecutionContext;
import com.portfolio.agent.turn.execution.TaskExecutionResult;
import com.portfolio.agent.turn.execution.TaskProvenance;
import com.portfolio.agent.turn.execution.TaskTerminalException;
import com.portfolio.agent.turn.execution.TaskTerminalReason;
import com.portfolio.agent.turn.planning.SemanticTask;

import java.util.List;
import java.util.Objects;

/** Straight-line Portfolio Task -> Invocation -> Capability -> Result -> Presentation -> Artifact. */
public final class PortfolioTaskExecutor implements SemanticTaskExecutor {
    private final PortfolioInvocationFactory invocationFactory;
    private final PortfolioEvidenceCapability capability;
    private final PortfolioSemanticResultFactory resultFactory;
    private final PortfolioPresentationComposer presentationComposer;
    private final PortfolioFactExpressionPort expressionPort;
    private final PortfolioFactExpressionCompiler expressionCompiler;

    public PortfolioTaskExecutor(
            PortfolioInvocationFactory invocationFactory,
            PortfolioEvidenceCapability capability,
            PortfolioSemanticResultFactory resultFactory,
            PortfolioPresentationComposer presentationComposer) {
        this(invocationFactory, capability, resultFactory, presentationComposer, null, null);
    }

    public PortfolioTaskExecutor(
            PortfolioInvocationFactory invocationFactory,
            PortfolioEvidenceCapability capability,
            PortfolioSemanticResultFactory resultFactory,
            PortfolioPresentationComposer presentationComposer,
            PortfolioFactExpressionPort expressionPort,
            PortfolioFactExpressionCompiler expressionCompiler) {
        this.invocationFactory = Objects.requireNonNull(invocationFactory, "invocationFactory");
        this.capability = Objects.requireNonNull(capability, "capability");
        this.resultFactory = Objects.requireNonNull(resultFactory, "resultFactory");
        this.presentationComposer = Objects.requireNonNull(
                presentationComposer, "presentationComposer");
        this.expressionPort = expressionPort;
        this.expressionCompiler = expressionCompiler;
        if ((expressionPort == null) != (expressionCompiler == null)) {
            throw new IllegalArgumentException("expression port/compiler must be paired");
        }
    }

    @Override public SemanticTask.SourceDomain getSourceDomain() {
        return SemanticTask.SourceDomain.PORTFOLIO;
    }

    @Override public TaskExecutionResult execute(TaskExecutionContext context) {
        try {
            PortfolioEvidenceInvocation invocation = invocationFactory.create(context);
            ValidatedEvidenceBundle evidence = capability.execute(invocation, context.getDeadline());
            PortfolioSemanticResult result = resultFactory.create(
                    context.getTask(), invocation, evidence).orElseThrow(() ->
                    new TaskTerminalException(
                            TaskTerminalException.Kind.NO_RESULT,
                            TaskTerminalReason.NO_SUPPORTED_RESULT));
            PortfolioPresentation presentation = presentationComposer.compose(result);
            if (result instanceof PortfolioSemanticResult.Fact fact
                    && expressionPort != null && !context.getDeadline().isExpired()) {
                try {
                    String draft = expressionPort.express(fact, presentation, context.getDeadline());
                    presentation = expressionCompiler.compile(fact, presentation, draft);
                } catch (RuntimeException ignored) {
                    // Canonical presentation remains the atomic fallback.
                }
            }
            List<String> sourceKeys = result.getUnits().stream()
                    .map(unit -> unit.getSourceReference().getReferenceKey()).distinct().toList();
            TaskArtifact artifact = new TaskArtifact(
                    result, presentation, new TaskProvenance(sourceKeys));
            return result.getCoverage() == PortfolioSemanticResult.Coverage.FULL
                    ? TaskExecutionResult.full(artifact) : TaskExecutionResult.partial(artifact);
        } catch (PortfolioEvidenceCapability.PortfolioCapabilityException failure) {
            throw new TaskTerminalException(
                    TaskTerminalException.Kind.FAILED, TaskTerminalReason.CAPABILITY_UNAVAILABLE);
        } catch (TaskTerminalException failure) {
            throw failure;
        } catch (IllegalArgumentException failure) {
            throw new TaskTerminalException(
                    TaskTerminalException.Kind.REJECTED, TaskTerminalReason.INPUT_REJECTED);
        }
    }
}
