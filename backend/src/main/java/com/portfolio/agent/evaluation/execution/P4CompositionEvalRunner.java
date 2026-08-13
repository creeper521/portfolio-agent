package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.answer.composition.domain.CompositionMode;
import com.portfolio.agent.answer.composition.domain.ExpressionDisposition;
import com.portfolio.agent.answer.composition.domain.PortfolioAnswerMaterial;
import com.portfolio.agent.answer.composition.domain.PortfolioCompositionContext;
import com.portfolio.agent.answer.composition.domain.PortfolioCompositionResult;
import com.portfolio.agent.answer.composition.service.DeterministicPortfolioAnswerComposer;
import com.portfolio.agent.answer.composition.service.PortfolioAnswerComposition;
import com.portfolio.agent.answer.domain.PortfolioAnswerPlan;
import com.portfolio.agent.evaluation.domain.P4SafetyCheck;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Runs Eval through the production PortfolioAnswerComposition seam. */
public final class P4CompositionEvalRunner {
    private final PortfolioAnswerComposition composition;
    private final DeterministicPortfolioAnswerComposer deterministicComposer;
    private final ExpressionAttemptProbe attemptProbe;

    public P4CompositionEvalRunner(PortfolioAnswerComposition composition,
            ExpressionAttemptProbe attemptProbe) {
        this(composition, new DeterministicPortfolioAnswerComposer(), attemptProbe);
    }

    P4CompositionEvalRunner(PortfolioAnswerComposition composition,
            DeterministicPortfolioAnswerComposer deterministicComposer,
            ExpressionAttemptProbe attemptProbe) {
        this.composition = Objects.requireNonNull(composition, "composition");
        this.deterministicComposer = Objects.requireNonNull(
                deterministicComposer, "deterministicComposer");
        this.attemptProbe = Objects.requireNonNull(attemptProbe, "attemptProbe");
    }

    public P4EvalSample run(PortfolioAnswerMaterial material,
            PortfolioCompositionContext context, boolean privacyCaptureSafe) {
        int attemptsBefore = attemptProbe.attemptCount();
        PortfolioCompositionResult result = composition.compose(material, context);
        int attemptDelta = attemptProbe.attemptCount() - attemptsBefore;
        PortfolioAnswerPlan deterministic = deterministicComposer.compose(material);
        boolean atomicFallback = result.getCompositionMode() != CompositionMode.FALLBACK
                || result.getPlan().equals(deterministic);
        boolean supportBinding = planReferenceKeys(result.getPlan())
                .stream().allMatch(materialReferenceKeys(material)::contains);
        boolean acceptedOrSafeClosed = result.getExpressionDisposition() == ExpressionDisposition.ACCEPTED
                || atomicFallback;
        EnumMap<P4SafetyCheck, Boolean> checks = new EnumMap<>(P4SafetyCheck.class);
        checks.put(P4SafetyCheck.SUPPORT_BINDING, supportBinding);
        checks.put(P4SafetyCheck.PROTECTED_ATOM, acceptedOrSafeClosed);
        checks.put(P4SafetyCheck.QUALIFIER_PRESERVATION, acceptedOrSafeClosed);
        checks.put(P4SafetyCheck.REQUIRED_COVERAGE, acceptedOrSafeClosed);
        checks.put(P4SafetyCheck.STRICT_SCHEMA, acceptedOrSafeClosed);
        checks.put(P4SafetyCheck.ATOMIC_FALLBACK, atomicFallback);
        if (attemptDelta < 0 || attemptDelta > 1) {
            throw new IllegalStateException("P4 evaluation observed an invalid attempt count");
        }
        return new P4EvalSample(checks, attemptDelta == 1, privacyCaptureSafe);
    }

    private Set<String> materialReferenceKeys(PortfolioAnswerMaterial material) {
        Set<String> keys = new LinkedHashSet<>();
        material.getStatements().forEach(statement -> statement.getPublicSourceReferences()
                .forEach(reference -> keys.add(reference.getReferenceKey())));
        return Set.copyOf(keys);
    }

    private Set<String> planReferenceKeys(PortfolioAnswerPlan plan) {
        Set<String> keys = new LinkedHashSet<>();
        plan.getSections().forEach(section -> section.getSourceReferences()
                .forEach(reference -> keys.add(reference.getReferenceKey())));
        return Set.copyOf(keys);
    }

    @FunctionalInterface
    public interface ExpressionAttemptProbe {
        int attemptCount();
    }
}
