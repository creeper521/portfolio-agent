package com.portfolio.agent.answer.routing.adapter.execution;

import com.portfolio.agent.answer.domain.GroundedAnswerContribution;
import com.portfolio.agent.answer.composition.domain.PortfolioAnswerMaterial;
import com.portfolio.agent.answer.domain.PublicSourceReferenceValue;
import com.portfolio.agent.answer.intelligence.execution.capability.CapabilityExecutionResult;
import com.portfolio.agent.answer.intelligence.execution.capability.PortfolioEvidenceCapability;
import com.portfolio.agent.answer.intelligence.execution.domain.CapabilityExecutionConstraints;
import com.portfolio.agent.answer.intelligence.execution.domain.CandidateSubject;
import com.portfolio.agent.answer.intelligence.execution.planning.PortfolioCapabilityCatalog;
import com.portfolio.agent.answer.intelligence.retrieval.CorpusBackend;
import com.portfolio.agent.answer.intelligence.execution.planning.PortfolioExecutionPlanner;
import com.portfolio.agent.answer.intelligence.execution.planning.PortfolioPlanValidator;
import com.portfolio.agent.answer.intelligence.execution.planning.TrustedPortfolioExecutionPlan;
import com.portfolio.agent.answer.intelligence.execution.resultpolicy.ComparisonResultPolicy;
import com.portfolio.agent.answer.intelligence.execution.resultpolicy.FactResultPolicy;
import com.portfolio.agent.answer.intelligence.execution.resultpolicy.PortfolioResultPolicy;
import com.portfolio.agent.answer.intelligence.execution.resultpolicy.RecommendationResultPolicy;
import com.portfolio.agent.answer.intelligence.execution.resultpolicy.RefineResultPolicy;
import com.portfolio.agent.answer.intelligence.execution.support.EvidenceSupportAssessment;
import com.portfolio.agent.answer.intelligence.execution.support.EvidenceSupportAssessor;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SemanticTaskType;
import com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.TaskSourceDomain;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import com.portfolio.agent.answer.routing.domain.SemanticTaskExecutionContext;
import com.portfolio.agent.answer.routing.domain.SemanticTaskParameters;
import com.portfolio.agent.answer.routing.domain.TaskOutcome;
import com.portfolio.agent.answer.routing.domain.TaskComposition;
import com.portfolio.agent.answer.routing.domain.TaskExecutionAllowance;
import com.portfolio.agent.answer.routing.domain.TaskResultProvenance;
import com.portfolio.agent.answer.routing.domain.TaskResultPayload;
import com.portfolio.agent.answer.routing.service.SemanticTaskExecutor;
import com.portfolio.agent.answer.composition.domain.PortfolioCompositionResult;
import com.portfolio.agent.answer.composition.service.PortfolioAnswerComposition;
import com.portfolio.agent.answer.composition.domain.AudienceRole;
import com.portfolio.agent.answer.composition.domain.ExpressionAllowance;
import com.portfolio.agent.answer.composition.domain.ExpressionIntent;
import com.portfolio.agent.answer.composition.domain.FactAnswerMaterial;
import com.portfolio.agent.answer.composition.domain.FocusMode;
import com.portfolio.agent.answer.composition.domain.LocaleCode;
import com.portfolio.agent.answer.composition.domain.PortfolioCompositionContext;
import com.portfolio.agent.answer.composition.domain.RequestedDimension;
import com.portfolio.agent.answer.composition.domain.RequestedFacet;
import com.portfolio.agent.answer.composition.domain.RequestedOutput;
import com.portfolio.agent.answer.composition.domain.ResponseDepth;
import com.portfolio.agent.answer.composition.domain.TaskKind;
import com.portfolio.agent.answer.composition.domain.TaskSource;
import com.portfolio.agent.answer.domain.PortfolioAnswerPlan;
import com.portfolio.agent.answer.domain.PortfolioAnswerSection;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.common.observability.DiagnosticEventPublisher;
import com.portfolio.agent.common.observability.DiagnosticLevel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Portfolio executor whose only answer-construction seam is P4 composition. */
public final class P3PortfolioSemanticTaskExecutor implements SemanticTaskExecutor {
    private final PortfolioExecutionPlanner planner;
    private final PortfolioPlanValidator validator;
    private final PortfolioEvidenceCapability capability;
    private final EvidenceSupportAssessor supportAssessor;
    private final PortfolioResultPolicy factPolicy;
    private final PortfolioResultPolicy comparisonPolicy;
    private final PortfolioResultPolicy recommendationPolicy;
    private final PortfolioResultPolicy refinePolicy;
    private final DiagnosticEventPublisher diagnosticEventPublisher;
    private final PortfolioAnswerComposition p4Composition;

    public P3PortfolioSemanticTaskExecutor(
            PortfolioCapabilityCatalog catalog,
            PortfolioEvidenceCapability capability) {
        this(new PortfolioExecutionPlanner(catalog), new PortfolioPlanValidator(catalog), capability,
                new EvidenceSupportAssessor(), new FactResultPolicy(),
                new ComparisonResultPolicy(), new RecommendationResultPolicy(), new RefineResultPolicy(),
                event -> { }, null);
    }

    public P3PortfolioSemanticTaskExecutor(
            PortfolioCapabilityCatalog catalog,
            PortfolioEvidenceCapability capability,
            DiagnosticEventPublisher diagnosticEventPublisher) {
        this(new PortfolioExecutionPlanner(catalog), new PortfolioPlanValidator(catalog), capability,
                new EvidenceSupportAssessor(), new FactResultPolicy(),
                new ComparisonResultPolicy(), new RecommendationResultPolicy(), new RefineResultPolicy(),
                diagnosticEventPublisher, null);
    }

    public P3PortfolioSemanticTaskExecutor(
            PortfolioCapabilityCatalog catalog,
            PortfolioEvidenceCapability capability,
            DiagnosticEventPublisher diagnosticEventPublisher,
            PortfolioAnswerComposition p4Composition) {
        this(new PortfolioExecutionPlanner(catalog), new PortfolioPlanValidator(catalog), capability,
                new EvidenceSupportAssessor(), new FactResultPolicy(),
                new ComparisonResultPolicy(), new RecommendationResultPolicy(), new RefineResultPolicy(),
                diagnosticEventPublisher, p4Composition);
    }

    public P3PortfolioSemanticTaskExecutor(
            PortfolioCapabilityCatalog catalog,
            CorpusBackend primaryBackend,
            PortfolioEvidenceCapability capability,
            DiagnosticEventPublisher diagnosticEventPublisher,
            PortfolioAnswerComposition p4Composition) {
        this(new PortfolioExecutionPlanner(catalog, primaryBackend),
                new PortfolioPlanValidator(catalog), capability,
                new EvidenceSupportAssessor(), new FactResultPolicy(),
                new ComparisonResultPolicy(), new RecommendationResultPolicy(), new RefineResultPolicy(),
                diagnosticEventPublisher, p4Composition);
    }

    public P3PortfolioSemanticTaskExecutor(
            PortfolioExecutionPlanner planner,
            PortfolioPlanValidator validator,
            PortfolioEvidenceCapability capability,
            EvidenceSupportAssessor supportAssessor,
            PortfolioResultPolicy factPolicy,
            PortfolioResultPolicy comparisonPolicy,
            PortfolioResultPolicy recommendationPolicy,
            PortfolioResultPolicy refinePolicy) {
        this(planner, validator, capability, supportAssessor, factPolicy,
                comparisonPolicy, recommendationPolicy, refinePolicy, event -> { });
    }

    public P3PortfolioSemanticTaskExecutor(
            PortfolioExecutionPlanner planner,
            PortfolioPlanValidator validator,
            PortfolioEvidenceCapability capability,
            EvidenceSupportAssessor supportAssessor,
            PortfolioResultPolicy factPolicy,
            PortfolioResultPolicy comparisonPolicy,
            PortfolioResultPolicy recommendationPolicy,
            PortfolioResultPolicy refinePolicy,
            DiagnosticEventPublisher diagnosticEventPublisher) {
        this(planner, validator, capability, supportAssessor, factPolicy,
                comparisonPolicy, recommendationPolicy, refinePolicy, diagnosticEventPublisher, null);
    }

    public P3PortfolioSemanticTaskExecutor(
            PortfolioExecutionPlanner planner,
            PortfolioPlanValidator validator,
            PortfolioEvidenceCapability capability,
            EvidenceSupportAssessor supportAssessor,
            PortfolioResultPolicy factPolicy,
            PortfolioResultPolicy comparisonPolicy,
            PortfolioResultPolicy recommendationPolicy,
            PortfolioResultPolicy refinePolicy,
            DiagnosticEventPublisher diagnosticEventPublisher,
            PortfolioAnswerComposition p4Composition) {
        this.planner = Objects.requireNonNull(planner, "planner");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.capability = Objects.requireNonNull(capability, "capability");
        this.supportAssessor = Objects.requireNonNull(supportAssessor, "supportAssessor");
        this.factPolicy = Objects.requireNonNull(factPolicy, "factPolicy");
        this.comparisonPolicy = Objects.requireNonNull(comparisonPolicy, "comparisonPolicy");
        this.recommendationPolicy = Objects.requireNonNull(recommendationPolicy, "recommendationPolicy");
        this.refinePolicy = Objects.requireNonNull(refinePolicy, "refinePolicy");
        this.diagnosticEventPublisher = Objects.requireNonNull(
                diagnosticEventPublisher, "diagnosticEventPublisher");
        this.p4Composition = p4Composition == null ? new PortfolioAnswerComposition() : p4Composition;
    }

    @Override
    public TaskSourceDomain getSourceDomain() {
        return TaskSourceDomain.PORTFOLIO;
    }

    @Override
    public TaskOutcome execute(SemanticTaskExecutionContext context) {
        Objects.requireNonNull(context, "context");
        SemanticTask task = context.getSemanticTask();
        if (task.getSourceDomain() != TaskSourceDomain.PORTFOLIO) {
            return TaskOutcome.notSupported(task.getTaskId(), TaskSourceDomain.PORTFOLIO,
                    false, "PORTFOLIO_TASK_UNSUPPORTED");
        }
        String failureStage = "PLANNING";
        try {
            TrustedPortfolioExecutionPlan trusted = validator.validate(planner.plan(context), context);
            failureStage = "CAPABILITY";
            CapabilityExecutionResult execution = capability.execute(
                    trusted.getInvocation().getInvocation(),
                    new CapabilityExecutionConstraints(context.getTaskExecutionAllowance()));
            failureStage = "COMPOSITION";
            return toOutcome(task, execution, context);
        } catch (IllegalArgumentException exception) {
            publishFailure(task, failureStage, "PORTFOLIO_EXECUTION_REJECTED");
            return TaskOutcome.create(task.getTaskId(), TaskOutcome.TaskExecutionStatus.REJECTED,
                    TaskOutcome.TaskResolution.REJECTED, TaskOutcome.TaskEvidenceState.NOT_APPLICABLE,
                    false, java.util.Set.of("PORTFOLIO_EXECUTION_REJECTED"), null,
                    TaskSourceDomain.PORTFOLIO, null, null);
        } catch (RuntimeException exception) {
            publishFailure(task, failureStage, "PORTFOLIO_EXECUTION_FAILED");
            return TaskOutcome.failed(task.getTaskId(), TaskSourceDomain.PORTFOLIO,
                    "PORTFOLIO_EXECUTION_FAILED");
        }
    }

    private void publishFailure(SemanticTask task, String failureStage, String failureCode) {
        try {
            diagnosticEventPublisher.publish(DiagnosticEvent.builder(
                            "portfolio.execution.failed", DiagnosticLevel.ERROR)
                    .field("failure.stage", failureStage)
                    .field("failure.code", failureCode)
                    .field("capability.code", PortfolioCapabilityCatalog.CAPABILITY_ID)
                    .field("task.type", task.getTaskType().name())
                    .build());
        } catch (RuntimeException ignored) {
            // Diagnostics are best effort and must not change the safe execution outcome.
        }
    }

    private TaskOutcome toOutcome(SemanticTask task, CapabilityExecutionResult execution,
            SemanticTaskExecutionContext context) {
        return switch (execution.getStatus()) {
            case UNAVAILABLE, TIMED_OUT -> TaskOutcome.capabilityUnavailable(
                    task.getTaskId(), TaskSourceDomain.PORTFOLIO,
                    execution.getSafeReasonCode().map(Enum::name).orElse("CAPABILITY_UNAVAILABLE"));
            case INTEGRITY_FAILED -> {
                publishFailure(task, "CAPABILITY", execution.getSafeReasonCode()
                        .map(Enum::name).orElse("EVIDENCE_INTEGRITY_FAILURE"));
                yield TaskOutcome.failed(
                        task.getTaskId(), TaskSourceDomain.PORTFOLIO, "EVIDENCE_INTEGRITY_FAILURE");
            }
            case SUCCESS, EMPTY -> compose(task, execution, context);
        };
    }

    private TaskOutcome compose(SemanticTask task, CapabilityExecutionResult execution,
            SemanticTaskExecutionContext context) {
        EvidenceSupportAssessment assessment = supportAssessor.assess(
                task, execution.getEvidenceBundle().orElseThrow());
        if (!assessment.isSupported()) {
            return TaskOutcome.notSupported(task.getTaskId(), TaskSourceDomain.PORTFOLIO,
                    execution.isDegraded(), "PORTFOLIO_EVIDENCE_INSUFFICIENT");
        }
        PortfolioResultPolicy policy = policy(task.getTaskType());
        PortfolioAnswerMaterial material = policy.material(task,
                execution.getEvidenceBundle().orElseThrow(), assessment,
                execution.getCandidateSet().orElseThrow().getCandidateSubjects());
        GroundedAnswerContribution contribution = material.toGroundedContribution();
        PortfolioCompositionResult composition = p4Composition.compose(
                material, compositionContext(task, material, context));
        TaskResultProvenance provenance = TaskResultProvenance.direct(
                TaskSourceDomain.PORTFOLIO, List.of(), List.of());
        TaskOutcome composedOutcome;
        if (task.getTaskType() == SemanticTaskType.PORTFOLIO_RECOMMEND
                && task.getParameters() instanceof SemanticTaskParameters.PortfolioRecommend parameters) {
            TaskResultPayload.RecommendationResultPayload payload = recommendationPayload(
                    task, parameters, execution, assessment, composition.getPlan());
            boolean incomplete = payload.getProjection().getActualSize() < parameters.getRequestedSize().getValue();
            if (assessment.getStatus() == EvidenceSupportAssessment.SupportStatus.PARTIAL || incomplete) {
                composedOutcome = TaskOutcome.partiallyAnsweredWithPayloadAndContribution(
                        task.getTaskId(), TaskSourceDomain.PORTFOLIO, payload, contribution,
                        provenance, execution.isDegraded());
            } else {
                composedOutcome = TaskOutcome.answeredWithPayloadAndContribution(
                        task.getTaskId(), TaskSourceDomain.PORTFOLIO, payload, contribution,
                        provenance, execution.isDegraded());
            }
            return attachComposition(composition, composedOutcome);
        }
        TaskResultPayload.SectionResultPayload payload = sectionPayload(composition.getPlan());
        if (assessment.getStatus() == EvidenceSupportAssessment.SupportStatus.PARTIAL) {
            composedOutcome = TaskOutcome.partiallyAnsweredWithPayloadAndContribution(
                    task.getTaskId(), TaskSourceDomain.PORTFOLIO, payload, contribution,
                    provenance, execution.isDegraded());
        } else {
            composedOutcome = TaskOutcome.answeredWithPayloadAndContribution(
                    task.getTaskId(), TaskSourceDomain.PORTFOLIO, payload, contribution,
                    provenance, execution.isDegraded());
        }
        return attachComposition(composition, composedOutcome);
    }

    private TaskOutcome attachComposition(PortfolioCompositionResult result, TaskOutcome outcome) {
        return outcome.withComposition(new TaskComposition(result.getCompositionMode(),
                result.isExpressionDegraded()));
    }

    private TaskResultPayload.RecommendationResultPayload recommendationPayload(
            SemanticTask task,
            SemanticTaskParameters.PortfolioRecommend parameters,
            CapabilityExecutionResult execution,
            EvidenceSupportAssessment assessment,
            PortfolioAnswerPlan plan) {
        java.util.Map<String, List<com.portfolio.agent.answer.intelligence.execution.validation.ValidatedEvidenceUnit>>
                unitsBySubject = assessment.getSelectedUnits().stream().collect(
                        java.util.stream.Collectors.groupingBy(
                                com.portfolio.agent.answer.intelligence.execution.validation.ValidatedEvidenceUnit::getSubjectId,
                                java.util.LinkedHashMap::new,
                                java.util.stream.Collectors.toList()));
        List<TaskResultPayload.RecommendationItem> items = execution.getCandidateSet().orElseThrow()
                .getCandidateSubjects().stream()
                .filter(subject -> unitsBySubject.containsKey(subject.getSubjectId()))
                .limit(parameters.getRequestedSize().getValue())
                .map(subject -> recommendationItem(subject, unitsBySubject.get(subject.getSubjectId())))
                .toList();
        if (items.isEmpty()) {
            throw new IllegalArgumentException("supported recommendation requires public items");
        }
        List<String> selectedIds = items.stream()
                .map(TaskResultPayload.RecommendationItem::getPortfolioId).toList();
        java.util.Set<String> capabilities = parameters.getCapabilityCodes().stream()
                .map(Enum::name)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        List<String> satisfied = new java.util.ArrayList<>();
        satisfied.add(parameters.getCareerTrack().name());
        satisfied.add(parameters.getAudienceRole().name());
        satisfied.addAll(capabilities);
        java.util.LinkedHashSet<String> reasons = new java.util.LinkedHashSet<>();
        if (items.size() < parameters.getRequestedSize().getValue()) {
            if (execution.getCandidateSet().orElseThrow().getCandidateSubjects().size()
                    < parameters.getRequestedSize().getValue()) {
                reasons.add("INSUFFICIENT_ELIGIBLE_PROJECTS");
            } else {
                reasons.add("INSUFFICIENT_EVIDENCE_SUPPORTED_PROJECTS");
            }
        }
        if (!assessment.getOmittedLabels().isEmpty()) {
            reasons.add("CAPABILITY_COVERAGE_INCOMPLETE");
        }
        TaskResultPayload.RecommendationProjection projection =
                new TaskResultPayload.RecommendationProjection(
                        batchId(task, execution.getCandidateSet().orElseThrow()
                                .getReturnedContentVersion(), selectedIds),
                        execution.getCandidateSet().orElseThrow().getReturnedContentVersion(),
                        parameters.getCareerTrack().name(), parameters.getAudienceRole().name(),
                        capabilities, parameters.getRequestedSize().getValue(), items.size(),
                        parameters.getCandidateSubjects().isEmpty()
                                ? TaskResultPayload.RecommendationProjection.CandidateScope.ALL_PUBLISHED_PROJECTS
                                : TaskResultPayload.RecommendationProjection.CandidateScope.EXPLICIT_PROJECT_SET,
                        selectedIds, items, List.copyOf(satisfied), assessment.getOmittedLabels(),
                        List.copyOf(reasons));
        return new TaskResultPayload.RecommendationResultPayload(
                projection, plan.getSections().stream()
                        .map(PortfolioAnswerSection::getContent).toList());
    }

    private TaskResultPayload.SectionResultPayload sectionPayload(PortfolioAnswerPlan plan) {
        List<TaskResultPayload.SectionBlock> sections = plan.getSections().stream().map(section ->
                new TaskResultPayload.SectionBlock(section.getSectionType(), section.getTitle(),
                        section.getContent(), section.getClaimIds(), section.getEvidenceIds(),
                        section.getSourceReferences())).toList();
        return TaskResultPayload.SectionResultPayload.fromSections(sections, plan.getSummary());
    }

    private PortfolioCompositionContext compositionContext(
            SemanticTask task, PortfolioAnswerMaterial material,
            SemanticTaskExecutionContext executionContext) {
        FocusMode focus = material instanceof FactAnswerMaterial fact
                ? fact.getFocusMode() : FocusMode.OVERVIEW;
        TaskKind kind = switch (task.getTaskType()) {
            case PORTFOLIO_FACT -> TaskKind.FACT;
            case PORTFOLIO_COMPARE -> TaskKind.COMPARISON;
            case PORTFOLIO_RECOMMEND -> TaskKind.RECOMMENDATION;
            case PORTFOLIO_REFINE_RECOMMENDATION -> TaskKind.REFINE_RECOMMENDATION;
            default -> throw new IllegalArgumentException("unsupported portfolio task kind");
        };
        List<RequestedFacet> facets = task.getParameters() instanceof
                SemanticTaskParameters.PortfolioFact fact ? fact.getFacets().stream()
                .map(this::requestedFacet).distinct().toList() : List.of();
        List<RequestedDimension> dimensions = task.getParameters() instanceof
                SemanticTaskParameters.PortfolioCompare comparison ? comparison.getDimensions().stream()
                .map(this::requestedDimension).distinct().toList() : List.of();
        List<RequestedOutput> outputs = task.getRequestedOutputs().stream()
                .map(this::requestedOutput).filter(Objects::nonNull).distinct().toList();
        AudienceRole audience = audienceRole(task);
        ExpressionIntent intent = new ExpressionIntent(kind, focus, facets, dimensions, outputs,
                audience, ResponseDepth.MEDIUM, LocaleCode.ZH_CN,
                executionContext.isPresetRequest() ? TaskSource.PRESET : TaskSource.FREE_TEXT,
                material.getPublicSubjectLabels());
        TaskExecutionAllowance taskAllowance = executionContext.getTaskExecutionAllowance();
        ExpressionAllowance allowance = new ExpressionAllowance(
                executionContext.isModelExpressionAttemptAllowed()
                        && kind == TaskKind.FACT && !executionContext.isPresetRequest(),
                taskAllowance.getAbsoluteDeadline(), taskAllowance.getCharacterLimit(), 16,
                executionContext.isModelExpressionAttemptAllowed() ? 1 : 0);
        return new PortfolioCompositionContext(intent, allowance);
    }

    private RequestedFacet requestedFacet(
            com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.PortfolioFacet facet) {
        return switch (facet) {
            case OVERVIEW -> RequestedFacet.BACKGROUND;
            case RESPONSIBILITY -> RequestedFacet.RESPONSIBILITY;
            case VERIFICATION -> RequestedFacet.VERIFICATION;
            case OUTCOME -> RequestedFacet.OUTCOME;
            case LIMITATION -> RequestedFacet.LIMITATION;
            default -> RequestedFacet.SOLUTION;
        };
    }

    private RequestedDimension requestedDimension(
            com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.ComparisonDimension dimension) {
        return switch (dimension) {
            case IMPLEMENTATION -> RequestedDimension.IMPLEMENTATION;
            case IMPACT -> RequestedDimension.OUTCOME;
            case RISKS -> RequestedDimension.LIMITATION;
            default -> RequestedDimension.TECHNICAL_DECISION;
        };
    }

    private RequestedOutput requestedOutput(
            com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.RequestedOutput output) {
        return switch (output) {
            case SUMMARY -> RequestedOutput.DIRECT_ANSWER;
            case EVIDENCE -> RequestedOutput.EVIDENCE_REFERENCES;
            case COMPARISON -> RequestedOutput.COMPARISON;
            case RECOMMENDATION -> RequestedOutput.RECOMMENDATION;
            default -> null;
        };
    }

    private AudienceRole audienceRole(SemanticTask task) {
        Object parameters = task.getParameters();
        String value = parameters instanceof SemanticTaskParameters.PortfolioFact fact
                ? fact.getAudienceRole().name()
                : parameters instanceof SemanticTaskParameters.PortfolioCompare comparison
                ? comparison.getAudienceRole().name()
                : parameters instanceof SemanticTaskParameters.PortfolioRecommend recommendation
                ? recommendation.getAudienceRole().name() : AudienceRole.GUEST.name();
        return AudienceRole.valueOf(value);
    }

    private TaskResultPayload.RecommendationItem recommendationItem(
            CandidateSubject subject,
            List<com.portfolio.agent.answer.intelligence.execution.validation.ValidatedEvidenceUnit> units) {
        List<String> reasons = units.stream().map(unit -> unit.getClaim().getStatement())
                .distinct().toList();
        List<PublicSourceReferenceValue> sources = units.stream()
                .map(unit -> unit.getSourceReference())
                .map(reference -> new PublicSourceReferenceValue(
                        reference.getReferenceKey(), reference.getLabel(),
                        reference.getPublishedVersion(), reference.getSourceType().name(),
                        reference.getSubjectRoute(), reference.getEvidenceRoute()))
                .distinct().toList();
        return new TaskResultPayload.RecommendationItem(
                subject.getSubjectId(), subject.getTitle(), subject.getSubjectRoute(), reasons,
                sources.stream().map(PublicSourceReferenceValue::getReferenceKey).toList(), sources);
    }

    private String batchId(SemanticTask task, String contentVersion, List<String> selectedIds) {
        try {
            String canonical = task.getTaskId() + "\n" + contentVersion + "\n"
                    + String.join("\n", selectedIds);
            return "rec_" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private PortfolioResultPolicy policy(SemanticTaskType taskType) {
        return switch (taskType) {
            case PORTFOLIO_FACT -> factPolicy;
            case PORTFOLIO_COMPARE -> comparisonPolicy;
            case PORTFOLIO_RECOMMEND -> recommendationPolicy;
            case PORTFOLIO_REFINE_RECOMMENDATION -> refinePolicy;
            default -> throw new IllegalArgumentException("unsupported portfolio task type");
        };
    }
}
