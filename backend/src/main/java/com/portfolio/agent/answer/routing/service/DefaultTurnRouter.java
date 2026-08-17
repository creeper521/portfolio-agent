package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.SemanticTurnInput;
import com.portfolio.agent.answer.routing.domain.SubjectReference;
import com.portfolio.agent.answer.routing.gateway.SemanticClassifierPort;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Fixed deterministic routing pipeline. This task deliberately has no model-classifier dependency. */
public final class DefaultTurnRouter implements TurnRouter {

    private final GlobalBoundaryGate boundaryGate;
    private final RoutingContextResolver contextResolver;
    private final PublicSubjectCatalog subjectCatalog;
    private final InputFormationPolicy inputFormationPolicy;
    private final SemanticSignalCollector signalCollector;
    private final SemanticPlanCompiler planCompiler;
    private final SemanticPlanValidator planValidator;
    private final TurnDecisionPolicy decisionPolicy;
    private final SemanticClassifierPort semanticClassifier;
    private final boolean semanticClassifierEnabled;

    public DefaultTurnRouter(
            GlobalBoundaryGate boundaryGate,
            RoutingContextResolver contextResolver,
            PublicSubjectCatalog subjectCatalog,
            SemanticSignalCollector signalCollector,
            SemanticPlanCompiler planCompiler,
            SemanticPlanValidator planValidator,
            TurnDecisionPolicy decisionPolicy) {
        this(boundaryGate, contextResolver, subjectCatalog, signalCollector, planCompiler,
                planValidator, decisionPolicy, null, false, new InputFormationPolicy());
    }

    DefaultTurnRouter(
            GlobalBoundaryGate boundaryGate,
            RoutingContextResolver contextResolver,
            PublicSubjectCatalog subjectCatalog,
            SemanticSignalCollector signalCollector,
            SemanticPlanCompiler planCompiler,
            SemanticPlanValidator planValidator,
            TurnDecisionPolicy decisionPolicy,
            SemanticClassifierPort semanticClassifier,
            boolean semanticClassifierEnabled) {
        this(boundaryGate, contextResolver, subjectCatalog, signalCollector, planCompiler,
                planValidator, decisionPolicy, semanticClassifier, semanticClassifierEnabled,
                new InputFormationPolicy());
    }

    DefaultTurnRouter(
            GlobalBoundaryGate boundaryGate,
            RoutingContextResolver contextResolver,
            PublicSubjectCatalog subjectCatalog,
            SemanticSignalCollector signalCollector,
            SemanticPlanCompiler planCompiler,
            SemanticPlanValidator planValidator,
            TurnDecisionPolicy decisionPolicy,
            SemanticClassifierPort semanticClassifier,
            boolean semanticClassifierEnabled,
            InputFormationPolicy inputFormationPolicy) {
        this.boundaryGate = Objects.requireNonNull(boundaryGate, "boundaryGate");
        this.contextResolver = Objects.requireNonNull(contextResolver, "contextResolver");
        this.subjectCatalog = Objects.requireNonNull(subjectCatalog, "subjectCatalog");
        this.inputFormationPolicy = Objects.requireNonNull(inputFormationPolicy, "inputFormationPolicy");
        this.signalCollector = Objects.requireNonNull(signalCollector, "signalCollector");
        this.planCompiler = Objects.requireNonNull(planCompiler, "planCompiler");
        this.planValidator = Objects.requireNonNull(planValidator, "planValidator");
        this.decisionPolicy = Objects.requireNonNull(decisionPolicy, "decisionPolicy");
        this.semanticClassifier = semanticClassifier;
        this.semanticClassifierEnabled = semanticClassifierEnabled && semanticClassifier != null;
    }

    /**
     * Public composition seam for the reviewed runtime snapshot. The
     * package-private catalog remains an implementation detail, while callers
     * can supply only stable public identifiers and display aliases.
     */
    public static DefaultTurnRouter fromPublicSubjects(
            List<PublicSubjectSpec> publicSubjects,
            GlobalBoundaryGate boundaryGate,
            RoutingContextResolver contextResolver,
            SemanticSignalCollector signalCollector,
            SemanticPlanCompiler planCompiler,
            SemanticPlanValidator planValidator,
            TurnDecisionPolicy decisionPolicy) {
        return fromPublicSubjects(publicSubjects, boundaryGate, contextResolver, signalCollector,
                planCompiler, planValidator, decisionPolicy, null, false);
    }

    public static DefaultTurnRouter fromPublicSubjects(
            List<PublicSubjectSpec> publicSubjects,
            GlobalBoundaryGate boundaryGate,
            RoutingContextResolver contextResolver,
            SemanticSignalCollector signalCollector,
            SemanticPlanCompiler planCompiler,
            SemanticPlanValidator planValidator,
            TurnDecisionPolicy decisionPolicy,
            SemanticClassifierPort semanticClassifier,
            boolean semanticClassifierEnabled) {
        Objects.requireNonNull(publicSubjects, "publicSubjects");
        Map<String, PublicSubjectSpec> distinct = new LinkedHashMap<>();
        for (PublicSubjectSpec subject : publicSubjects) {
            PublicSubjectSpec nonNullSubject = Objects.requireNonNull(subject, "publicSubject");
            String key = nonNullSubject.getSubjectType().name() + ':' + nonNullSubject.getSubjectId();
            if (distinct.put(key, nonNullSubject) != null) {
                throw new IllegalArgumentException("public subject specs must not contain duplicates");
            }
        }
        List<PublicSubjectCatalog.Subject> catalogSubjects = distinct.values().stream()
                .map(subject -> new PublicSubjectCatalog.Subject(
                        subject.getSubjectType(), subject.getSubjectId(),
                        subject.getContentVersion(), subject.getDisplayLabel(), subject.getAliases()))
                .toList();
        return new DefaultTurnRouter(
                boundaryGate,
                contextResolver,
                new PublicSubjectCatalog(catalogSubjects),
                signalCollector,
                planCompiler,
                planValidator,
                decisionPolicy,
                semanticClassifier,
                semanticClassifierEnabled);
    }

    @Override
    public SemanticTurnDecision route(SemanticTurnInput input) {
        Objects.requireNonNull(input, "input");
        GlobalBoundaryGate.BoundaryDecision boundary = boundaryGate.evaluate(input);
        if (boundary.isBoundary()) {
            return SemanticTurnDecision.boundary(new LinkedHashSet<>(boundary.getReasonCodes()));
        }
        if (input.getAction() != SemanticTurnInput.Action.CONFIRM_PLAN
                && inputFormationPolicy.evaluate(input.getRoutingQuestion())
                == InputFormationPolicy.Formation.UNFORMED) {
            return SemanticTurnDecision.clarificationRequired(
                    ClarificationRequest.unformedRequest());
        }
        if (input.getAction() == SemanticTurnInput.Action.CONFIRM_PLAN) {
            return SemanticTurnDecision.rejected(Set.of("ROUTING_CONFIRMATION_VERIFICATION_REQUIRED"));
        }
        ResolvedRoutingContext context = contextResolver.resolve(input, subjectCatalog);
        if (context.getStatus() == RoutingContextStatus.INVALID_INPUT) {
            return SemanticTurnDecision.rejected(Set.of("ROUTING_INPUT_INVALID"));
        }
        if (context.getStatus() == RoutingContextStatus.UNRESOLVED
                && "ROUTING_SUBJECT_INVALID_REFERENCE".equals(context.getReasonCode())) {
            return SemanticTurnDecision.rejected(Set.of("ROUTING_SUBJECT_INVALID_REFERENCE"));
        }
        if (context.getStatus() == RoutingContextStatus.UNRESOLVED && semanticClassifierEnabled) {
            context = tryModelSubjectResolution(input, context);
        }
        if (context.getStatus() == RoutingContextStatus.AMBIGUOUS) {
            return SemanticTurnDecision.clarificationRequired(
                    ClarificationRequest.contextConflict(subjectOptions(null)));
        }
        SemanticSignals signals = signalCollector.collect(input, context);
        if (signals.getRequestedTaskCount() > 6) {
            return SemanticTurnDecision.clarificationRequired(
                    ClarificationRequest.splitRequired(signals.getRequestedTaskCount()));
        }
        if (signals.getClarificationNeed() == SemanticSignals.ClarificationNeed.CRITICAL) {
            ClarificationRequest clarification = signals.getGoals().isEmpty()
                    ? ClarificationRequest.unformedRequest()
                    : signals.hasUnresolvedPortfolioGoal()
                    ? ClarificationRequest.contextConflict(subjectOptions(null))
                    : ClarificationRequest.comparisonSubjects(
                            ClarificationRequest.Scope.CRITICAL, 0,
                            subjectOptions(
                                    com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType.PROJECT,
                                    context.getSubjects()),
                            List.of());
            return SemanticTurnDecision.clarificationRequired(clarification);
        }
        String contract = input.getAgentTurnContract() == null ? "stp-v1" : input.getAgentTurnContract();
        PlanValidationResult validation = planValidator.validate(
                planCompiler.compile(signals, subjectCatalog.currentContentVersion()), contract);
        ClarificationRequest clarification = signals.getClarificationNeed() == SemanticSignals.ClarificationNeed.LOCAL
                ? ClarificationRequest.comparisonSubjects(
                        ClarificationRequest.Scope.LOCAL, signals.getGoals().size(),
                        subjectOptions(
                                com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType.PROJECT,
                                context.getSubjects()),
                        signals.getGoals().stream().map(this::goalLabel).toList())
                : null;
        return decisionPolicy.decide(validation, clarification);
    }

    private ResolvedRoutingContext tryModelSubjectResolution(
            SemanticTurnInput input, ResolvedRoutingContext unresolved) {
        try {
            SemanticClassifierPort.SemanticClassificationResult result = semanticClassifier.classify(
                    new SemanticClassifierPort.SemanticClassificationInput(
                            input.getRoutingQuestion(), subjectCatalog.references()));
            if (!result.isSuccessful()) {
                return unresolved;
            }
            List<SubjectReference> candidates = result.getTaskCandidates().stream()
                    .flatMap(candidate -> candidate.getSubjects().stream())
                    .distinct()
                    .toList();
            return contextResolver.resolveValidatedModelCandidates(unresolved, candidates, subjectCatalog);
        } catch (RuntimeException exception) {
            return unresolved;
        }
    }

    private List<ClarificationRequest.Option> subjectOptions(
            com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType requiredType) {
        return subjectOptions(requiredType, List.of());
    }

    private List<ClarificationRequest.Option> subjectOptions(
            com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType requiredType,
            List<SubjectReference> excludedSubjects) {
        Set<String> excludedIds = excludedSubjects.stream()
                .map(SubjectReference::getSubjectId).collect(java.util.stream.Collectors.toSet());
        return subjectCatalog.list(requiredType).stream()
                .filter(subject -> !excludedIds.contains(subject.getSubjectId()))
                .limit(8)
                .map(subject -> new ClarificationRequest.Option(
                        subject.getSubjectId(), subject.getDisplayLabel(),
                        subject.getSubjectType().name(), subject.getSubjectId()))
                .toList();
    }

    private String goalLabel(SemanticSignals.GoalCandidate goal) {
        return switch (goal.getIntent()) {
            case PORTFOLIO_FACT -> "介绍公开项目";
            case PORTFOLIO_COMPARE -> "比较公开项目";
            case PORTFOLIO_RECOMMEND -> "给出岗位推荐";
            case PORTFOLIO_REFINE_RECOMMENDATION -> "调整岗位推荐";
            case GENERAL_EXPLANATION -> "解释通用概念";
            case GENERAL_COMPARISON -> "比较通用主题";
            case SYNTHESIS -> "形成综合结论";
        };
    }

    /** Public, immutable projection of reviewed subject metadata only. */
    public static final class PublicSubjectSpec {

        private final com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType subjectType;
        private final String subjectId;
        private final String contentVersion;
        private final String displayLabel;
        private final Set<String> aliases;

        public PublicSubjectSpec(
                com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType subjectType,
                String subjectId,
                String contentVersion,
                Set<String> aliases) {
            this(subjectType, subjectId, contentVersion, subjectId, aliases);
        }

        public PublicSubjectSpec(
                com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType subjectType,
                String subjectId,
                String contentVersion,
                String displayLabel,
                Set<String> aliases) {
            this.subjectType = Objects.requireNonNull(subjectType, "subjectType");
            this.subjectId = requireText(subjectId, "subjectId");
            this.contentVersion = requireText(contentVersion, "contentVersion");
            this.displayLabel = requireText(displayLabel, "displayLabel");
            Objects.requireNonNull(aliases, "aliases");
            Set<String> copiedAliases = new LinkedHashSet<>();
            for (String alias : aliases) {
                copiedAliases.add(requireText(alias, "aliases"));
            }
            this.aliases = Set.copyOf(copiedAliases);
        }

        public com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType getSubjectType() {
            return subjectType;
        }

        public String getSubjectId() {
            return subjectId;
        }

        public String getContentVersion() {
            return contentVersion;
        }

        public String getDisplayLabel() { return displayLabel; }

        public Set<String> getAliases() {
            return aliases;
        }

        @Override
        public String toString() {
            return "PublicSubjectSpec{subjectType=" + subjectType + ", hasSubjectId=true, aliasCount="
                    + aliases.size() + '}';
        }

        private static String requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " is required");
            }
            return value.trim();
        }
    }
}
