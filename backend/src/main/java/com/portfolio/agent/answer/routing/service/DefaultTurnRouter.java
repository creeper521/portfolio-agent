package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.SemanticTurnInput;

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
    private final SemanticSignalCollector signalCollector;
    private final SemanticPlanCompiler planCompiler;
    private final SemanticPlanValidator planValidator;
    private final TurnDecisionPolicy decisionPolicy;

    public DefaultTurnRouter(
            GlobalBoundaryGate boundaryGate,
            RoutingContextResolver contextResolver,
            PublicSubjectCatalog subjectCatalog,
            SemanticSignalCollector signalCollector,
            SemanticPlanCompiler planCompiler,
            SemanticPlanValidator planValidator,
            TurnDecisionPolicy decisionPolicy) {
        this.boundaryGate = Objects.requireNonNull(boundaryGate, "boundaryGate");
        this.contextResolver = Objects.requireNonNull(contextResolver, "contextResolver");
        this.subjectCatalog = Objects.requireNonNull(subjectCatalog, "subjectCatalog");
        this.signalCollector = Objects.requireNonNull(signalCollector, "signalCollector");
        this.planCompiler = Objects.requireNonNull(planCompiler, "planCompiler");
        this.planValidator = Objects.requireNonNull(planValidator, "planValidator");
        this.decisionPolicy = Objects.requireNonNull(decisionPolicy, "decisionPolicy");
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
                        subject.getContentVersion(), subject.getAliases()))
                .toList();
        return new DefaultTurnRouter(
                boundaryGate,
                contextResolver,
                new PublicSubjectCatalog(catalogSubjects),
                signalCollector,
                planCompiler,
                planValidator,
                decisionPolicy);
    }

    @Override
    public SemanticTurnDecision route(SemanticTurnInput input) {
        Objects.requireNonNull(input, "input");
        GlobalBoundaryGate.BoundaryDecision boundary = boundaryGate.evaluate(input);
        if (boundary.isBoundary()) {
            return SemanticTurnDecision.boundary(new LinkedHashSet<>(boundary.getReasonCodes()));
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
        if (context.getStatus() == RoutingContextStatus.AMBIGUOUS) {
            return SemanticTurnDecision.clarificationRequired(ClarificationRequest.contextConflict());
        }
        SemanticSignals signals = signalCollector.collect(input, context);
        if (signals.getRequestedTaskCount() > 6) {
            return SemanticTurnDecision.clarificationRequired(
                    ClarificationRequest.splitRequired(signals.getRequestedTaskCount()));
        }
        if (signals.getClarificationNeed() == SemanticSignals.ClarificationNeed.CRITICAL) {
            ClarificationRequest clarification = signals.hasUnresolvedPortfolioGoal()
                    ? ClarificationRequest.contextConflict()
                    : ClarificationRequest.comparisonSubjects(ClarificationRequest.Scope.CRITICAL, 0);
            return SemanticTurnDecision.clarificationRequired(clarification);
        }
        String contract = input.getAgentTurnContract() == null ? "stp-v1" : input.getAgentTurnContract();
        PlanValidationResult validation = planValidator.validate(planCompiler.compile(signals), contract);
        ClarificationRequest clarification = signals.getClarificationNeed() == SemanticSignals.ClarificationNeed.LOCAL
                ? ClarificationRequest.comparisonSubjects(
                        ClarificationRequest.Scope.LOCAL, signals.getGoals().size())
                : null;
        return decisionPolicy.decide(validation, clarification);
    }

    /** Public, immutable projection of reviewed subject metadata only. */
    public static final class PublicSubjectSpec {

        private final com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType subjectType;
        private final String subjectId;
        private final String contentVersion;
        private final Set<String> aliases;

        public PublicSubjectSpec(
                com.portfolio.agent.answer.routing.domain.SemanticRoutingTypes.SubjectType subjectType,
                String subjectId,
                String contentVersion,
                Set<String> aliases) {
            this.subjectType = Objects.requireNonNull(subjectType, "subjectType");
            this.subjectId = requireText(subjectId, "subjectId");
            this.contentVersion = requireText(contentVersion, "contentVersion");
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
