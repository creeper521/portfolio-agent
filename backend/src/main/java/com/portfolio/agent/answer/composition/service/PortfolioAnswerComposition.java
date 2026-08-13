package com.portfolio.agent.answer.composition.service;

import com.portfolio.agent.answer.composition.assembly.ModelDraftPlanAssembler;
import com.portfolio.agent.answer.composition.codec.PortfolioExpressionDraftCodec;
import com.portfolio.agent.answer.composition.domain.CompositionMode;
import com.portfolio.agent.answer.composition.domain.ExpressionDisposition;
import com.portfolio.agent.answer.composition.domain.MaterialKind;
import com.portfolio.agent.answer.composition.domain.ModelExpressionDeadline;
import com.portfolio.agent.answer.composition.domain.ModelExpressionRequest;
import com.portfolio.agent.answer.composition.domain.ModelExpressionResult;
import com.portfolio.agent.answer.composition.domain.PortfolioAnswerMaterial;
import com.portfolio.agent.answer.composition.domain.PortfolioCompositionContext;
import com.portfolio.agent.answer.composition.domain.PortfolioCompositionResult;
import com.portfolio.agent.answer.composition.domain.draft.ModelExpressionDraft;
import com.portfolio.agent.answer.composition.domain.draft.FactExpressionDraft;
import com.portfolio.agent.answer.composition.domain.FactAnswerMaterial;
import com.portfolio.agent.answer.composition.gateway.PortfolioExpressionPort;
import com.portfolio.agent.answer.composition.projection.ExpressionInputDocument;
import com.portfolio.agent.answer.composition.projection.ModelExpressionInputProjector;
import com.portfolio.agent.answer.composition.validation.FactDraftValidator;
import com.portfolio.agent.answer.domain.PortfolioAnswerPlan;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Deep P4 module: expression failure can only atomically select the prebuilt fallback. */
public final class PortfolioAnswerComposition {

    private final DeterministicPortfolioAnswerComposer deterministicComposer;
    private final PortfolioAnswerPlanValidator planValidator;
    private final PortfolioExpressionPort expressionPort;
    private final ModelExpressionInputProjector inputProjector;
    private final PortfolioExpressionDraftCodec draftCodec;
    private final ExpressionCircuitBreaker circuitBreaker;
    private final FactDraftValidator factDraftValidator;
    private final ModelExpressionEligibilityPolicy eligibilityPolicy;
    private final ModelDraftPlanAssembler planAssembler;
    private final Clock clock;
    private final boolean expressionEnabled;
    private final PortfolioCompositionDiagnostics diagnostics;

    public PortfolioAnswerComposition() {
        this(new DeterministicPortfolioAnswerComposer(), new PortfolioAnswerPlanValidator(), null,
                new ModelExpressionInputProjector(), new PortfolioExpressionDraftCodec(),
                new ExpressionCircuitBreaker(), new FactDraftValidator(),
                new ModelExpressionEligibilityPolicy(),
                Clock.systemUTC(), false, PortfolioCompositionDiagnostics.noOp());
    }

    public PortfolioAnswerComposition(DeterministicPortfolioAnswerComposer deterministicComposer,
            PortfolioAnswerPlanValidator planValidator, PortfolioExpressionPort expressionPort) {
        this(deterministicComposer, planValidator, expressionPort,
                new ModelExpressionInputProjector(), new PortfolioExpressionDraftCodec(),
                new ExpressionCircuitBreaker(), new FactDraftValidator(),
                new ModelExpressionEligibilityPolicy(),
                Clock.systemUTC(), expressionPort != null, PortfolioCompositionDiagnostics.noOp());
    }

    public PortfolioAnswerComposition(DeterministicPortfolioAnswerComposer deterministicComposer,
            PortfolioAnswerPlanValidator planValidator, PortfolioExpressionPort expressionPort,
            ModelExpressionInputProjector inputProjector,
            PortfolioExpressionDraftCodec draftCodec,
            ExpressionCircuitBreaker circuitBreaker) {
        this(deterministicComposer, planValidator, expressionPort, inputProjector, draftCodec,
                circuitBreaker, new FactDraftValidator(),
                new ModelExpressionEligibilityPolicy(), Clock.systemUTC(), expressionPort != null,
                PortfolioCompositionDiagnostics.noOp());
    }

    public PortfolioAnswerComposition(DeterministicPortfolioAnswerComposer deterministicComposer,
            PortfolioAnswerPlanValidator planValidator, PortfolioExpressionPort expressionPort,
            ModelExpressionInputProjector inputProjector,
            PortfolioExpressionDraftCodec draftCodec,
            ExpressionCircuitBreaker circuitBreaker,
            FactDraftValidator factDraftValidator) {
        this(deterministicComposer, planValidator, expressionPort, inputProjector, draftCodec,
                circuitBreaker, factDraftValidator,
                new ModelExpressionEligibilityPolicy(), Clock.systemUTC(), expressionPort != null,
                PortfolioCompositionDiagnostics.noOp());
    }

    public PortfolioAnswerComposition(DeterministicPortfolioAnswerComposer deterministicComposer,
            PortfolioAnswerPlanValidator planValidator, PortfolioExpressionPort expressionPort,
            ModelExpressionInputProjector inputProjector,
            PortfolioExpressionDraftCodec draftCodec,
            ExpressionCircuitBreaker circuitBreaker,
            FactDraftValidator factDraftValidator,
            ModelExpressionEligibilityPolicy eligibilityPolicy,
            Clock clock,
            boolean expressionEnabled) {
        this(deterministicComposer, planValidator, expressionPort, inputProjector, draftCodec,
                circuitBreaker, factDraftValidator, eligibilityPolicy, clock, expressionEnabled,
                PortfolioCompositionDiagnostics.noOp());
    }

    public PortfolioAnswerComposition(DeterministicPortfolioAnswerComposer deterministicComposer,
            PortfolioAnswerPlanValidator planValidator, PortfolioExpressionPort expressionPort,
            ModelExpressionInputProjector inputProjector,
            PortfolioExpressionDraftCodec draftCodec,
            ExpressionCircuitBreaker circuitBreaker,
            FactDraftValidator factDraftValidator,
            ModelExpressionEligibilityPolicy eligibilityPolicy,
            Clock clock,
            boolean expressionEnabled,
            PortfolioCompositionDiagnostics diagnostics) {
        this.deterministicComposer = Objects.requireNonNull(
                deterministicComposer, "deterministicComposer");
        this.planValidator = Objects.requireNonNull(planValidator, "planValidator");
        this.expressionPort = expressionPort;
        this.inputProjector = Objects.requireNonNull(inputProjector, "inputProjector");
        this.draftCodec = Objects.requireNonNull(draftCodec, "draftCodec");
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker");
        this.factDraftValidator = Objects.requireNonNull(factDraftValidator, "factDraftValidator");
        this.eligibilityPolicy = Objects.requireNonNull(eligibilityPolicy, "eligibilityPolicy");
        this.planAssembler = new ModelDraftPlanAssembler();
        this.clock = Objects.requireNonNull(clock, "clock");
        this.expressionEnabled = expressionEnabled;
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    public PortfolioCompositionResult compose(
            PortfolioAnswerMaterial material, PortfolioCompositionContext context) {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(context, "context");
        int characterLimit = context.getExpressionAllowance().getCharacterLimit();
        PortfolioAnswerPlan fallback = deterministicComposer.compose(material);
        planValidator.validate(fallback, characterLimit);

        ModelExpressionEligibilityPolicy.Decision preliminary = eligibilityPolicy.evaluate(
                expressionEnabled && expressionPort != null, material, context.getExpressionIntent(),
                context.getExpressionAllowance(), 0, Instant.now(clock));
        if (preliminary != ModelExpressionEligibilityPolicy.Decision.ELIGIBLE) {
            PortfolioCompositionResult result = notAttempted(fallback, preliminary);
            diagnostics.eligibility(context.getExpressionIntent().getTaskKind(),
                    material.getMaterialKind(), result.getExpressionDisposition(), false, 0,
                    circuitBreaker.getState());
            return result;
        }

        ExpressionInputDocument input;
        try {
            input = inputProjector.project(material, context);
        } catch (RuntimeException exception) {
            diagnostics.validation(material.getMaterialKind(), false,
                    PortfolioCompositionDiagnostics.FailureCode.PLAN_INVALID);
            return diagnosedFallback(fallback, ExpressionDisposition.FALLBACK_PLAN_INVALID,
                    PortfolioCompositionDiagnostics.FailureCode.PLAN_INVALID);
        }
        ModelExpressionEligibilityPolicy.Decision projected = eligibilityPolicy.evaluate(
                true, material, context.getExpressionIntent(), context.getExpressionAllowance(),
                input.isOverLimit() ? Integer.MAX_VALUE : input.getSerializedJson().length(),
                Instant.now(clock));
        if (projected != ModelExpressionEligibilityPolicy.Decision.ELIGIBLE) {
            PortfolioCompositionResult result = notAttempted(fallback, projected);
            diagnostics.eligibility(context.getExpressionIntent().getTaskKind(),
                    material.getMaterialKind(), result.getExpressionDisposition(), false,
                    input.getSerializedJson().length(), circuitBreaker.getState());
            return result;
        }
        if (!circuitBreaker.tryAcquire()) {
            diagnostics.eligibility(context.getExpressionIntent().getTaskKind(),
                    material.getMaterialKind(), ExpressionDisposition.FALLBACK_CIRCUIT_OPEN,
                    false, input.getSerializedJson().length(), circuitBreaker.getState());
            return diagnosedFallback(fallback, ExpressionDisposition.FALLBACK_CIRCUIT_OPEN,
                    PortfolioCompositionDiagnostics.FailureCode.CIRCUIT_OPEN);
        }
        ModelExpressionEligibilityPolicy.Decision callTimeDecision = eligibilityPolicy.evaluate(
                true, material, context.getExpressionIntent(), context.getExpressionAllowance(),
                input.getSerializedJson().length(), Instant.now(clock));
        if (callTimeDecision == ModelExpressionEligibilityPolicy.Decision.DEADLINE) {
            circuitBreaker.recordNeutralCompletion();
            diagnostics.eligibility(context.getExpressionIntent().getTaskKind(),
                    material.getMaterialKind(), ExpressionDisposition.NOT_ATTEMPTED_DEADLINE,
                    false, input.getSerializedJson().length(), circuitBreaker.getState());
            return deterministic(fallback, ExpressionDisposition.NOT_ATTEMPTED_DEADLINE);
        }
        diagnostics.eligibility(context.getExpressionIntent().getTaskKind(), material.getMaterialKind(),
                ExpressionDisposition.ACCEPTED, true, input.getSerializedJson().length(),
                circuitBreaker.getState());

        ModelExpressionResult providerResult;
        try {
            providerResult = expressionPort.express(
                    new ModelExpressionRequest(
                            ModelExpressionInputProjector.SCHEMA_VERSION,
                            input.getSerializedJson()),
                    new ModelExpressionDeadline(
                            context.getExpressionAllowance().getAbsoluteDeadline()));
        } catch (RuntimeException exception) {
            circuitBreaker.recordEligibleFailure();
            return diagnosedFallback(fallback, ExpressionDisposition.FALLBACK_PROVIDER_FAILURE,
                    PortfolioCompositionDiagnostics.FailureCode.PROVIDER_FAILURE);
        }
        if (providerResult == null || providerResult.isEmpty()) {
            circuitBreaker.recordEligibleFailure();
            return diagnosedFallback(fallback, ExpressionDisposition.FALLBACK_EMPTY_RESPONSE,
                    PortfolioCompositionDiagnostics.FailureCode.EMPTY_RESPONSE);
        }

        ModelExpressionDraft draft;
        try {
            draft = draftCodec.decode(providerResult.getResponse(), MaterialKind.FACT);
        } catch (RuntimeException exception) {
            circuitBreaker.recordEligibleFailure();
            diagnostics.validation(material.getMaterialKind(), false,
                    PortfolioCompositionDiagnostics.FailureCode.SCHEMA_INVALID);
            return diagnosedFallback(fallback, ExpressionDisposition.FALLBACK_SCHEMA_INVALID,
                    PortfolioCompositionDiagnostics.FailureCode.SCHEMA_INVALID);
        }
        try {
            if (!(draft instanceof FactExpressionDraft factDraft)
                    || !(material instanceof FactAnswerMaterial factMaterial)) {
                throw new IllegalArgumentException("fact expression contract required");
            }
            factDraftValidator.validate(factDraft, factMaterial, input.getAliases(), context);
        } catch (RuntimeException exception) {
            circuitBreaker.recordEligibleFailure();
            diagnostics.validation(material.getMaterialKind(), false,
                    PortfolioCompositionDiagnostics.FailureCode.GROUNDING_INVALID);
            return diagnosedFallback(fallback, ExpressionDisposition.FALLBACK_GROUNDING_INVALID,
                    PortfolioCompositionDiagnostics.FailureCode.GROUNDING_INVALID);
        }

        try {
            PortfolioAnswerPlan modelPlan = planAssembler.assemble(
                    material, draft, input.getAliases(), characterLimit);
            planValidator.validate(modelPlan, characterLimit);
            circuitBreaker.recordSuccess();
            diagnostics.validation(material.getMaterialKind(), true, null);
            return new PortfolioCompositionResult(modelPlan, CompositionMode.MODEL_GROUNDED,
                    ExpressionDisposition.ACCEPTED, false);
        } catch (RuntimeException exception) {
            circuitBreaker.recordNeutralCompletion();
            diagnostics.validation(material.getMaterialKind(), false,
                    PortfolioCompositionDiagnostics.FailureCode.PLAN_INVALID);
            return diagnosedFallback(fallback, ExpressionDisposition.FALLBACK_PLAN_INVALID,
                    PortfolioCompositionDiagnostics.FailureCode.PLAN_INVALID);
        }
    }

    private PortfolioCompositionResult notAttempted(PortfolioAnswerPlan plan,
            ModelExpressionEligibilityPolicy.Decision decision) {
        return deterministic(plan, switch (decision) {
            case DISABLED -> ExpressionDisposition.NOT_ATTEMPTED_DISABLED;
            case INELIGIBLE -> ExpressionDisposition.NOT_ATTEMPTED_INELIGIBLE;
            case NO_ALLOWANCE -> ExpressionDisposition.NOT_ATTEMPTED_ALLOWANCE;
            case DEADLINE -> ExpressionDisposition.NOT_ATTEMPTED_DEADLINE;
            case INPUT_LIMIT -> ExpressionDisposition.NOT_ATTEMPTED_INPUT_LIMIT;
            case ELIGIBLE -> throw new IllegalArgumentException("eligible decision is not terminal");
        });
    }

    private PortfolioCompositionResult deterministic(
            PortfolioAnswerPlan plan, ExpressionDisposition disposition) {
        return new PortfolioCompositionResult(
                plan, CompositionMode.DETERMINISTIC, disposition, false);
    }

    private PortfolioCompositionResult fallback(
            PortfolioAnswerPlan plan, ExpressionDisposition disposition) {
        return new PortfolioCompositionResult(plan, CompositionMode.FALLBACK, disposition, true);
    }

    private PortfolioCompositionResult diagnosedFallback(PortfolioAnswerPlan plan,
            ExpressionDisposition disposition, PortfolioCompositionDiagnostics.FailureCode failureCode) {
        diagnostics.fallback(disposition, failureCode, circuitBreaker.getState());
        return fallback(plan, disposition);
    }
}
