package com.portfolio.agent.answer.routing.adapter.execution;

import com.portfolio.agent.answer.intelligence.domain.PortfolioConditions;
import com.portfolio.agent.answer.intelligence.domain.PortfolioDecision;
import com.portfolio.agent.answer.intelligence.domain.PortfolioDisposition;
import com.portfolio.agent.answer.intelligence.domain.PortfolioIntelligenceResult;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendation;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationContext;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRecommendationItem;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRefinement;
import com.portfolio.agent.answer.intelligence.domain.PortfolioRetrievedPassage;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTask;
import com.portfolio.agent.answer.intelligence.domain.PortfolioTaskMode;
import com.portfolio.agent.answer.intelligence.service.PortfolioIntelligence;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SemanticTaskType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskSourceDomain;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.TaskOutcome;
import com.portfolio.agent.answer.routing.domain.TaskResultPayload;
import com.portfolio.agent.answer.routing.domain.TaskResultProvenance;
import com.portfolio.agent.answer.routing.service.SemanticTaskExecutor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Adapts an already classified portfolio task to the existing verified portfolio capability. */
public final class PortfolioSemanticTaskExecutor implements SemanticTaskExecutor {

    private final PortfolioIntelligence portfolioIntelligence;
    private final RecommendationContextResolver recommendationContextResolver;
    private final String currentContentVersion;

    public PortfolioSemanticTaskExecutor(PortfolioIntelligence portfolioIntelligence) {
        this(portfolioIntelligence, ignored -> Optional.empty(), null);
    }

    public PortfolioSemanticTaskExecutor(
            PortfolioIntelligence portfolioIntelligence,
            RecommendationContextResolver recommendationContextResolver,
            String currentContentVersion) {
        this.portfolioIntelligence = Objects.requireNonNull(portfolioIntelligence, "portfolioIntelligence");
        this.recommendationContextResolver = Objects.requireNonNull(
                recommendationContextResolver, "recommendationContextResolver");
        this.currentContentVersion = normalizeNullable(currentContentVersion);
    }

    @Override
    public TaskSourceDomain getSourceDomain() {
        return TaskSourceDomain.PORTFOLIO;
    }

    @Override
    public TaskOutcome execute(SemanticTask task, List<TaskOutcome> availableDependencyOutcomes) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(availableDependencyOutcomes, "availableDependencyOutcomes");
        if (task.getSourceDomain() != TaskSourceDomain.PORTFOLIO || !isSupported(task.getTaskType())) {
            return TaskOutcome.notSupported(task.getTaskId(), TaskSourceDomain.PORTFOLIO,
                    false, "PORTFOLIO_TASK_UNSUPPORTED");
        }
        if (task.getTaskType() == SemanticTaskType.PORTFOLIO_REFINE_RECOMMENDATION) {
            RefinementResolution resolution = refinementResolution(task);
            if (!resolution.isExecutable()) {
                return TaskOutcome.notSupported(task.getTaskId(), TaskSourceDomain.PORTFOLIO,
                        false, resolution.getReasonCode());
            }
            PortfolioDecision decision = portfolioIntelligence.resolveTypedTask(
                    toPortfolioTask(task, resolution.getContext()));
            return toOutcome(task, decision);
        }
        PortfolioDecision decision = portfolioIntelligence.resolveTypedTask(toPortfolioTask(task, null));
        return toOutcome(task, decision);
    }

    private PortfolioTask toPortfolioTask(
            SemanticTask task, PortfolioRecommendationContext recommendationContext) {
        PortfolioTaskMode mode = switch (task.getTaskType()) {
            case PORTFOLIO_FACT -> PortfolioTaskMode.FACT_LOOKUP;
            case PORTFOLIO_COMPARE -> PortfolioTaskMode.COMPARISON;
            case PORTFOLIO_RECOMMEND -> PortfolioTaskMode.RECOMMENDATION;
            case PORTFOLIO_REFINE_RECOMMENDATION -> PortfolioTaskMode.REFINE_RECOMMENDATION;
            default -> throw new IllegalArgumentException("unsupported portfolio semantic task type");
        };
        PortfolioConditions conditions = conditions(task.getParameters());
        List<String> subjectIds = subjectIds(task.getParameters());
        PortfolioRefinement refinement = refinement(task.getParameters());
        return new PortfolioTask(
                task.getTaskId(),
                query(task),
                mode,
                1.0d,
                conditions,
                recommendationContext,
                refinement,
                subjectIds,
                List.of());
    }

    private String query(SemanticTask task) {
        if (task.getParameters() instanceof SemanticTaskParameters.PortfolioFact fact) {
            return task.getGoalLabel() + " facets=" + fact.getFacets();
        }
        if (task.getParameters() instanceof SemanticTaskParameters.PortfolioCompare comparison) {
            return task.getGoalLabel() + " dimensions=" + comparison.getDimensions();
        }
        if (task.getParameters() instanceof SemanticTaskParameters.PortfolioRecommend recommendation) {
            return task.getGoalLabel() + " capabilities=" + recommendation.getCapabilityCodes();
        }
        if (task.getParameters() instanceof SemanticTaskParameters.PortfolioRefinement) {
            return task.getGoalLabel();
        }
        throw new IllegalArgumentException("unsupported portfolio parameters");
    }

    private PortfolioConditions conditions(SemanticTaskParameters parameters) {
        if (parameters instanceof SemanticTaskParameters.PortfolioRecommend recommendation) {
            Set<String> capabilityCodes = new LinkedHashSet<>();
            recommendation.getCapabilityCodes().forEach(value -> capabilityCodes.add(value.name()));
            return new PortfolioConditions(
                    recommendation.getCareerTrack().name(),
                    recommendation.getAudienceRole().name(),
                    capabilityCodes,
                    recommendation.getGoal(),
                    recommendation.getRequestedSize().getValue());
        }
        if (parameters instanceof SemanticTaskParameters.PortfolioFact fact) {
            return new PortfolioConditions(null, fact.getAudienceRole().name(), Set.of(), null, null);
        }
        if (parameters instanceof SemanticTaskParameters.PortfolioCompare comparison) {
            return new PortfolioConditions(null, comparison.getAudienceRole().name(), Set.of(), null, null);
        }
        if (parameters instanceof SemanticTaskParameters.PortfolioRefinement refinement
                && refinement.getAddedConstraints().isEmpty()) {
            return PortfolioConditions.empty();
        }
        throw new IllegalArgumentException("unsupported portfolio parameters");
    }

    private PortfolioRefinement refinement(SemanticTaskParameters parameters) {
        if (!(parameters instanceof SemanticTaskParameters.PortfolioRefinement refinement)) {
            return null;
        }
        if (!refinement.getAddedConstraints().isEmpty()) {
            throw new IllegalArgumentException("unsupported portfolio refinement constraints");
        }
        Set<String> removedSubjects = new LinkedHashSet<>();
        refinement.getRemovedSubjects().forEach(subject -> removedSubjects.add(subject.getSubjectId()));
        return new PortfolioRefinement(PortfolioConditions.empty(), removedSubjects);
    }

    private RefinementResolution refinementResolution(SemanticTask task) {
        if (!(task.getParameters() instanceof SemanticTaskParameters.PortfolioRefinement refinement)) {
            return RefinementResolution.rejected("PORTFOLIO_REFINEMENT_CONTEXT_MISSING", null);
        }
        String resultReference = refinement.getBaseResultReference().getSubjectId();
        Optional<PortfolioRecommendationContext> context = recommendationContextResolver.resolve(
                resultReference);
        if (context.isEmpty()) {
            return RefinementResolution.rejected("PORTFOLIO_REFINEMENT_CONTEXT_MISSING", null);
        }
        PortfolioRecommendationContext resolved = context.get();
        if (!resultReference.equals(resolved.getRecommendationBatchId())) {
            return RefinementResolution.rejected("PORTFOLIO_REFINEMENT_CONTEXT_MISMATCH", null);
        }
        if (currentContentVersion == null
                || !currentContentVersion.equals(resolved.getContentVersion())) {
            return RefinementResolution.rejected("PORTFOLIO_REFINEMENT_CONTEXT_STALE", null);
        }
        if (!refinement.getAddedConstraints().isEmpty()) {
            return RefinementResolution.rejected("PORTFOLIO_REFINEMENT_CONSTRAINT_UNSUPPORTED", null);
        }
        return RefinementResolution.accepted(resolved);
    }

    private List<String> subjectIds(SemanticTaskParameters parameters) {
        if (parameters instanceof SemanticTaskParameters.PortfolioFact fact) {
            return List.of(fact.getSubject().getSubjectId());
        }
        if (parameters instanceof SemanticTaskParameters.PortfolioCompare comparison) {
            return comparison.getSubjects().stream().map(value -> value.getSubjectId()).toList();
        }
        if (parameters instanceof SemanticTaskParameters.PortfolioRecommend recommendation) {
            return recommendation.getCandidateSubjects().stream()
                    .map(value -> value.getSubjectId()).toList();
        }
        return List.of();
    }

    private TaskOutcome toOutcome(SemanticTask task, PortfolioDecision decision) {
        if (decision == null) {
            return TaskOutcome.failed(task.getTaskId(), TaskSourceDomain.PORTFOLIO,
                    "PORTFOLIO_CAPABILITY_FAILURE");
        }
        return switch (decision.getDisposition()) {
            case ANSWERED -> answered(task, decision.getMaterial().orElse(null));
            case NOT_SUPPORTED -> TaskOutcome.notSupported(task.getTaskId(), TaskSourceDomain.PORTFOLIO,
                    false, "PORTFOLIO_EVIDENCE_INSUFFICIENT");
            case CAPABILITY_UNAVAILABLE -> TaskOutcome.capabilityUnavailable(
                    task.getTaskId(), TaskSourceDomain.PORTFOLIO, "PORTFOLIO_CAPABILITY_UNAVAILABLE");
            case NEEDS_CLARIFICATION, INVALID_INPUT, NOT_PORTFOLIO -> TaskOutcome.notSupported(
                    task.getTaskId(), TaskSourceDomain.PORTFOLIO, false, "PORTFOLIO_TASK_NOT_SUPPORTED");
        };
    }

    private TaskOutcome answered(SemanticTask task, PortfolioIntelligenceResult material) {
        if (material == null || material.getEvidence().isEmpty()) {
            return TaskOutcome.notSupported(task.getTaskId(), TaskSourceDomain.PORTFOLIO,
                    false, "PORTFOLIO_EVIDENCE_INSUFFICIENT");
        }
        List<String> claimIds = new ArrayList<>();
        List<String> evidenceIds = new ArrayList<>();
        List<String> blocks = new ArrayList<>();
        for (PortfolioRetrievedPassage passage : material.getEvidence()) {
            blocks.add(passage.getContent());
            addDistinct(claimIds, passage.getClaimId());
            for (String evidenceId : passage.getEvidenceIds()) {
                addDistinct(evidenceIds, evidenceId);
            }
        }
        if (claimIds.isEmpty() || evidenceIds.isEmpty() || blocks.isEmpty()) {
            return TaskOutcome.notSupported(task.getTaskId(), TaskSourceDomain.PORTFOLIO,
                    material.isDegraded(), "PORTFOLIO_EVIDENCE_INSUFFICIENT");
        }
        boolean recommendationTask = task.getTaskType() == SemanticTaskType.PORTFOLIO_RECOMMEND
                || task.getTaskType() == SemanticTaskType.PORTFOLIO_REFINE_RECOMMENDATION;
        if (recommendationTask
                && (material.getPortfolioRecommendation() == null
                || material.getPortfolioRecommendation().getItems().isEmpty())) {
            return TaskOutcome.notSupported(task.getTaskId(), TaskSourceDomain.PORTFOLIO,
                    material.isDegraded(), "PORTFOLIO_RECOMMENDATION_UNAVAILABLE");
        }
        TaskResultProvenance provenance = TaskResultProvenance.direct(
                TaskSourceDomain.PORTFOLIO, claimIds, evidenceIds);
        TaskResultPayload payload = recommendationTask
                ? recommendationPayload(material.getPortfolioRecommendation(), blocks)
                : new TaskResultPayload.SectionResultPayload(blocks, task.getGoalLabel());
        return TaskOutcome.answered(task.getTaskId(), TaskSourceDomain.PORTFOLIO,
                payload, provenance, material.isDegraded());
    }

    private TaskResultPayload recommendationPayload(
            PortfolioRecommendation recommendation, List<String> supportingBlocks) {
        List<TaskResultPayload.RecommendationItem> entries = new ArrayList<>();
        for (PortfolioRecommendationItem item : recommendation.getItems()) {
            entries.add(new TaskResultPayload.RecommendationItem(
                    item.getPortfolioId(),
                    item.getTitle(),
                    item.getRoute(),
                    item.getMatchReasons(),
                    item.getEvidenceIds()));
        }
        return new TaskResultPayload.RecommendationResultPayload(entries, supportingBlocks);
    }

    private boolean isSupported(SemanticTaskType taskType) {
        return taskType == SemanticTaskType.PORTFOLIO_FACT
                || taskType == SemanticTaskType.PORTFOLIO_COMPARE
                || taskType == SemanticTaskType.PORTFOLIO_RECOMMEND
                || taskType == SemanticTaskType.PORTFOLIO_REFINE_RECOMMENDATION;
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void addDistinct(List<String> values, String value) {
        if (value != null && !value.isBlank() && !values.contains(value.trim())) {
            values.add(value.trim());
        }
    }

    @FunctionalInterface
    public interface RecommendationContextResolver {

        Optional<PortfolioRecommendationContext> resolve(String baseResultReference);
    }

    private static final class RefinementResolution {

        private final String reasonCode;
        private final PortfolioRecommendationContext context;

        private RefinementResolution(String reasonCode, PortfolioRecommendationContext context) {
            this.reasonCode = reasonCode;
            this.context = context;
        }

        private static RefinementResolution accepted(PortfolioRecommendationContext context) {
            return new RefinementResolution(null, context);
        }

        private static RefinementResolution rejected(
                String reasonCode, PortfolioRecommendationContext context) {
            return new RefinementResolution(reasonCode, context);
        }

        private boolean isExecutable() {
            return context != null;
        }

        private String getReasonCode() {
            return reasonCode;
        }

        private PortfolioRecommendationContext getContext() {
            return context;
        }
    }
}
