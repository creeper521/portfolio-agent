package com.portfolio.agent.turn.capability.synthesis;

import com.portfolio.agent.turn.capability.general.GeneralSemanticResult;
import com.portfolio.agent.turn.capability.portfolio.semantic.PortfolioSemanticResult;
import com.portfolio.agent.turn.execution.SemanticTaskExecutor;
import com.portfolio.agent.turn.execution.TaskArtifact;
import com.portfolio.agent.turn.execution.TaskExecutionContext;
import com.portfolio.agent.turn.execution.TaskExecutionResult;
import com.portfolio.agent.turn.execution.TaskProvenance;
import com.portfolio.agent.turn.execution.TaskSemanticResult;
import com.portfolio.agent.turn.execution.TaskTerminalException;
import com.portfolio.agent.turn.execution.TaskTerminalReason;
import com.portfolio.agent.turn.planning.SemanticTask;
import com.portfolio.agent.turn.planning.UserGoalProposal;

import java.util.List;
import java.util.Objects;

/** Exact one-General plus one-Portfolio fan-in anchored by the planned concept. */
public final class CrossDomainTaskExecutor implements SemanticTaskExecutor {
    private final CrossDomainPresentationComposer presentationComposer;

    public CrossDomainTaskExecutor(CrossDomainPresentationComposer presentationComposer) {
        this.presentationComposer = Objects.requireNonNull(presentationComposer, "presentationComposer");
    }

    @Override public SemanticTask.SourceDomain getSourceDomain() { return SemanticTask.SourceDomain.SYNTHESIS; }

    @Override public TaskExecutionResult execute(TaskExecutionContext context) {
        Objects.requireNonNull(context, "context");
        if (context.getTask().getSourceDomain() != SemanticTask.SourceDomain.SYNTHESIS
                || !(context.getTask().getParameters().getParameters()
                instanceof UserGoalProposal.ApplyConceptParameters parameters)) {
            throw new TaskTerminalException(TaskTerminalException.Kind.REJECTED, TaskTerminalReason.INPUT_REJECTED);
        }
        GeneralSemanticResult general = exactlyOne(context.getDependencyResults(), GeneralSemanticResult.class);
        PortfolioSemanticResult portfolio = exactlyOne(context.getDependencyResults(), PortfolioSemanticResult.class);
        if (general == null || portfolio == null || context.getDependencyResults().size() != 2) {
            throw new TaskTerminalException(TaskTerminalException.Kind.NO_RESULT, TaskTerminalReason.DEPENDENCY_UNAVAILABLE);
        }
        String concept = parameters.getConceptAnchor().getText();
        if (!normalize(concept).equals(normalize(general.getTopic()))) {
            throw new TaskTerminalException(TaskTerminalException.Kind.NO_RESULT, TaskTerminalReason.NO_SUPPORTED_RESULT);
        }
        List<GeneralSemanticResult.Statement> selectedGeneral = general.getStatements().stream()
                .filter(value -> value.getRole() == GeneralSemanticResult.Role.DEFINITION
                        || value.getRole() == GeneralSemanticResult.Role.MECHANISM)
                .toList();
        List<CrossDomainSemanticResult.GroundedPortfolioStatement> selectedPortfolio =
                portfolio.getUnits().stream().map(value ->
                        new CrossDomainSemanticResult.GroundedPortfolioStatement(
                                value.getSubjectId(), value.getClaim().getCategory(),
                                value.getClaim().getStatement(),
                                value.getSourceReference())).toList();
        if (selectedGeneral.isEmpty() || selectedPortfolio.isEmpty()) {
            throw new TaskTerminalException(TaskTerminalException.Kind.NO_RESULT, TaskTerminalReason.NO_SUPPORTED_RESULT);
        }
        CrossDomainSemanticResult result = new CrossDomainSemanticResult(
                concept, selectedGeneral, selectedPortfolio, general.getCaveats());
        List<String> sourceKeys = selectedPortfolio.stream()
                .map(value -> value.sourceReference().getReferenceKey()).distinct().toList();
        return TaskExecutionResult.full(new TaskArtifact(
                result, presentationComposer.compose(result), new TaskProvenance(sourceKeys)));
    }

    private <T extends TaskSemanticResult> T exactlyOne(
            List<TaskSemanticResult> values, Class<T> type) {
        List<T> matches = values.stream().filter(type::isInstance).map(type::cast).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
    }
}
