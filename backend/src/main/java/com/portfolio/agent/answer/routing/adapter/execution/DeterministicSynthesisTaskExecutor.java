package com.portfolio.agent.answer.routing.adapter.execution;

import com.portfolio.agent.turn.capability.portfolio.semantic.PortfolioSemanticResult;
import com.portfolio.agent.answer.domain.AnswerSectionType;
import com.portfolio.agent.answer.domain.PublicSourceReferenceValue;
import com.portfolio.agent.answer.general.domain.GeneralAnswerMaterial;
import com.portfolio.agent.answer.synthesis.domain.AllowedRelation;
import com.portfolio.agent.answer.synthesis.service.CrossDomainExpressionPipeline;
import com.portfolio.agent.answer.synthesis.service.CrossDomainRelationPolicy;
import com.portfolio.agent.answer.synthesis.service.DeterministicCrossDomainComposer;
import com.portfolio.agent.turn.execution.SectionedTaskPresentation;
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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Synthesis consumes semantic results only; rendered upstream text is never an input. */
public final class DeterministicSynthesisTaskExecutor implements SemanticTaskExecutor {
    private final boolean relationsEnabled;
    private final CrossDomainRelationPolicy relationPolicy;
    private final DeterministicCrossDomainComposer composer;
    private final CrossDomainExpressionPipeline expressionPipeline;

    public DeterministicSynthesisTaskExecutor() {
        this(false, new CrossDomainRelationPolicy(), new DeterministicCrossDomainComposer(), null);
    }

    public DeterministicSynthesisTaskExecutor(
            boolean relationsEnabled,
            CrossDomainRelationPolicy relationPolicy,
            DeterministicCrossDomainComposer composer) {
        this(relationsEnabled, relationPolicy, composer, null);
    }

    public DeterministicSynthesisTaskExecutor(
            boolean relationsEnabled,
            CrossDomainRelationPolicy relationPolicy,
            DeterministicCrossDomainComposer composer,
            CrossDomainExpressionPipeline expressionPipeline) {
        this.relationsEnabled = relationsEnabled;
        this.relationPolicy = Objects.requireNonNull(relationPolicy, "relationPolicy");
        this.composer = Objects.requireNonNull(composer, "composer");
        this.expressionPipeline = expressionPipeline;
    }

    @Override public SemanticTask.SourceDomain getSourceDomain() {
        return SemanticTask.SourceDomain.SYNTHESIS;
    }

    @Override public TaskExecutionResult execute(TaskExecutionContext context) {
        SemanticTask task = Objects.requireNonNull(context, "context").getTask();
        if (task.getSourceDomain() != SemanticTask.SourceDomain.SYNTHESIS
                || !(task.getParameters().getParameters()
                instanceof UserGoalProposal.ApplyConceptParameters parameters)) {
            throw new TaskTerminalException(
                    TaskTerminalException.Kind.REJECTED, TaskTerminalReason.INPUT_REJECTED);
        }
        GeneralAnswerMaterial general = context.getDependencyResults().stream()
                .filter(GeneralAnswerMaterial.class::isInstance)
                .map(GeneralAnswerMaterial.class::cast).findFirst().orElse(null);
        PortfolioSemanticResult portfolio = context.getDependencyResults().stream()
                .filter(PortfolioSemanticResult.class::isInstance)
                .map(PortfolioSemanticResult.class::cast).findFirst().orElse(null);
        if (general == null || portfolio == null) {
            throw new TaskTerminalException(
                    TaskTerminalException.Kind.NO_RESULT, TaskTerminalReason.DEPENDENCY_UNAVAILABLE);
        }
        if (!relationsEnabled) {
            throw new TaskTerminalException(
                    TaskTerminalException.Kind.FAILED, TaskTerminalReason.CAPABILITY_UNAVAILABLE);
        }
        List<String> generalStatements = general.getStatements().stream()
                .map(statement -> statement.getText()).toList();
        List<String> portfolioStatements = portfolio.getUnits().stream()
                .map(unit -> unit.getClaim().getStatement()).toList();
        Set<String> concepts = Set.of(parameters.getConceptAnchor().getText());
        List<AllowedRelation> relations = relationPolicy.allow(
                "general-result", "portfolio-result",
                String.join("\n", generalStatements), String.join("\n", portfolioStatements), concepts);
        if (relations.isEmpty()) {
            throw new TaskTerminalException(
                    TaskTerminalException.Kind.NO_RESULT, TaskTerminalReason.NO_SUPPORTED_RESULT);
        }
        List<String> blocks = new ArrayList<>(composer.composeAll(
                generalStatements, portfolioStatements, relations.getFirst()));
        if (context.isModelExpressionAllowed() && expressionPipeline != null) {
            expressionPipeline.express(
                    String.join("\n", generalStatements), String.join("\n", portfolioStatements),
                    relations.getFirst()).ifPresent(expression -> {
                        blocks.clear();
                        blocks.add(expression);
                    });
        }
        if (blocks.isEmpty()) {
            throw new TaskTerminalException(
                    TaskTerminalException.Kind.NO_RESULT, TaskTerminalReason.NO_SUPPORTED_RESULT);
        }
        List<PublicSourceReferenceValue> sources = portfolio.getUnits().stream()
                .map(unit -> unit.getSourceReference())
                .map(value -> new PublicSourceReferenceValue(
                        value.getReferenceKey(), value.getLabel(), value.getPublishedVersion(),
                        value.getSourceType(), value.getSubjectRoute(), value.getEvidenceRoute()))
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toMap(
                                PublicSourceReferenceValue::getReferenceKey, value -> value,
                                (left, right) -> left, java.util.LinkedHashMap::new),
                        values -> List.copyOf(values.values())));
        List<SectionedTaskPresentation.Section> sections = blocks.stream()
                .map(block -> new SectionedTaskPresentation.Section(
                        AnswerSectionType.SOLUTION, "关联", block, sources)).toList();
        CrossDomainSemanticResult result = new CrossDomainSemanticResult(
                parameters.getConceptAnchor().getText(), generalStatements, portfolioStatements);
        return TaskExecutionResult.full(new TaskArtifact(
                result, new SectionedTaskPresentation(null, sections),
                new TaskProvenance(sources.stream()
                        .map(PublicSourceReferenceValue::getReferenceKey).toList())));
    }

    public static final class CrossDomainSemanticResult implements TaskSemanticResult {
        private final String concept;
        private final List<String> generalStatements;
        private final List<String> portfolioStatements;
        public CrossDomainSemanticResult(
                String concept, List<String> generalStatements, List<String> portfolioStatements) {
            this.concept = Objects.requireNonNull(concept, "concept");
            this.generalStatements = List.copyOf(generalStatements);
            this.portfolioStatements = List.copyOf(portfolioStatements);
        }
        public String getConcept() { return concept; }
        public List<String> getGeneralStatements() { return generalStatements; }
        public List<String> getPortfolioStatements() { return portfolioStatements; }
    }
}
