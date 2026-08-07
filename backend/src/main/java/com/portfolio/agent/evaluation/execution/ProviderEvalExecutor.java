package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.answer.domain.ConversationAnswerBlock;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.ConversationDraft;
import com.portfolio.agent.answer.domain.ConversationIntent;
import com.portfolio.agent.answer.domain.ConversationModelFailureCode;
import com.portfolio.agent.answer.domain.ConversationModelResult;
import com.portfolio.agent.answer.domain.ConversationRoute;
import com.portfolio.agent.answer.domain.ConversationWindow;
import com.portfolio.agent.answer.domain.PortfolioGroundingContext;
import com.portfolio.agent.answer.gateway.ConversationalModelPort;
import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.evaluation.domain.EvalAnswerShape;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalMessage;
import com.portfolio.agent.evaluation.domain.EvalObservation;
import com.portfolio.agent.evaluation.domain.EvalObservationStatus;
import com.portfolio.agent.evaluation.domain.EvalProviderUsage;
import com.portfolio.agent.evaluation.domain.EvalProviderUsageAvailability;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Provider layer that consumes the production ConversationalModelPort seam and
 * never introduces a new provider registry. The response body is converted to
 * ids/counts/closed classification and never retained.
 */
public final class ProviderEvalExecutor implements EvalExecutor {

    private final ConversationalModelPort modelPort;
    private final String providerName;

    public ProviderEvalExecutor(
            ConversationalModelPort modelPort,
            String providerName) {
        this.modelPort = Objects.requireNonNull(modelPort, "modelPort");
        this.providerName = Objects.requireNonNull(providerName, "providerName");
    }

    @Override
    public boolean supports(EvalLayer layer) {
        return layer == EvalLayer.PROVIDER;
    }

    @Override
    public EvalObservation execute(EvalExecutionInput input, EvalRunContext context) {
        String question = firstUserMessage(input.getMessages());
        if (question == null || question.isBlank()) {
            return error(input, "CLIENT_INVALID");
        }
        ConversationRoute route = new ConversationRoute(
                ConversationIntent.PORTFOLIO_GROUNDED,
                ConversationAnswerScope.PORTFOLIO, 1.0d, null, null,
                null, false);
        ConversationWindow window = new ConversationWindow(null, List.of(), 0);
        PortfolioGroundingContext grounding = new PortfolioGroundingContext(
                null, List.of(), List.of(), List.of());
        long startedAt = System.nanoTime();
        ConversationModelResult<ConversationDraft> result;
        try {
            result = modelPort.generate(question, window, route, grounding);
        } catch (RuntimeException failure) {
            return failed(input, elapsed(startedAt),
                    EvalProviderUsage.unavailable(), "PROVIDER_ERROR");
        }
        long duration = elapsed(startedAt);
        if (!result.isSuccessful()) {
            return failed(input, duration, EvalProviderUsage.unavailable(),
                    failureCode(result.getFailureCode()));
        }
        ConversationDraft draft = result.getValue();
        List<ConversationAnswerBlock> blocks = draft.getBlocks() == null
                ? List.of() : draft.getBlocks();
        if (blocks.isEmpty()) {
            return failed(input, duration, EvalProviderUsage.unavailable(),
                    "PROVIDER_EMPTY");
        }
        List<String> claims = new ArrayList<>();
        List<String> evidence = new ArrayList<>();
        for (ConversationAnswerBlock block : blocks) {
            claims.addAll(block.getClaimIds());
            evidence.addAll(block.getEvidenceIds());
        }
        EvalAnswerShape shape = EvalAnswerShape.from(blocks);
        // no real token/billing data flows back from the model seam, so usage
        // is recorded as unavailable instead of fabricating zero values
        EvalProviderUsage usage = EvalProviderUsage.unavailable();
        boolean passed = draft.getResolution() == AnswerResolution.ANSWERED;
        return new EvalObservation(
                input.getCaseId(), input.getLayer(), input.getTrialIndex(),
                passed ? EvalObservationStatus.PASS : EvalObservationStatus.FAIL,
                null, null, List.copyOf(claims), List.copyOf(evidence), List.of(),
                draft.getResolution() == null ? AnswerResolution.NOT_SUPPORTED
                        : draft.getResolution(),
                ConversationAnswerScope.PORTFOLIO, GenerationMode.MODEL,
                AnswerSource.RETRIEVAL, List.of(passed ? "PROVIDER_SUCCESS" : "PROVIDER_FALLBACK"),
                duration, usage, shape, false, true);
    }

    private EvalObservation failed(
            EvalExecutionInput input,
            long duration,
            EvalProviderUsage usage,
            String reasonCode) {
        return new EvalObservation(
                input.getCaseId(), input.getLayer(), input.getTrialIndex(),
                EvalObservationStatus.FAIL,
                null, null, List.of(), List.of(), List.of(),
                AnswerResolution.CAPABILITY_UNAVAILABLE, ConversationAnswerScope.PORTFOLIO,
                GenerationMode.MODEL, AnswerSource.RETRIEVAL,
                List.of(reasonCode), duration, usage,
                EvalAnswerShape.empty(), false, true);
    }

    private EvalObservation error(EvalExecutionInput input, String reasonCode) {
        return new EvalObservation(
                input.getCaseId(), input.getLayer(), input.getTrialIndex(),
                EvalObservationStatus.ERROR,
                null, null, List.of(), List.of(), List.of(),
                AnswerResolution.INVALID_INPUT, ConversationAnswerScope.PORTFOLIO,
                GenerationMode.MODEL, AnswerSource.RETRIEVAL,
                List.of(reasonCode), 0L, EvalProviderUsage.unavailable(),
                EvalAnswerShape.empty(), false, true);
    }

    private String failureCode(ConversationModelFailureCode code) {
        return switch (code) {
            case TIMEOUT -> "PROVIDER_TIMEOUT";
            case EMPTY_RESPONSE -> "PROVIDER_EMPTY";
            case INVALID_RESPONSE -> "PROVIDER_INVALID";
            case DISABLED -> "PROVIDER_DISABLED";
            case REQUEST_BUILD_FAILED, PROVIDER_ERROR -> "PROVIDER_ERROR";
        };
    }

    private String firstUserMessage(List<EvalMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        return messages.get(0).getContent();
    }

    private long elapsed(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }
}
