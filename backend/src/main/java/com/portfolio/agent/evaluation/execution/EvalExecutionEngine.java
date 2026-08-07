package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.evaluation.domain.EvalAnswerShape;
import com.portfolio.agent.evaluation.domain.EvalCase;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalMessage;
import com.portfolio.agent.evaluation.domain.EvalObservation;
import com.portfolio.agent.evaluation.domain.EvalObservationStatus;
import com.portfolio.agent.evaluation.domain.EvalProviderUsage;
import com.portfolio.agent.evaluation.domain.EvalRunMode;
import com.portfolio.agent.evaluation.domain.EvalRiskLevel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class EvalExecutionEngine {

    private final Map<EvalLayer, EvalExecutor> executors;

    public EvalExecutionEngine(List<EvalExecutor> executorList) {
        Objects.requireNonNull(executorList, "executorList");
        Map<EvalLayer, EvalExecutor> registered = new LinkedHashMap<>();
        for (EvalExecutor executor : executorList) {
            for (EvalLayer layer : EvalLayer.values()) {
                if (executor.supports(layer)) {
                    registered.putIfAbsent(layer, executor);
                }
            }
        }
        this.executors = Map.copyOf(registered);
    }

    public List<EvalObservation> execute(EvalRunPlan plan, EvalRunContext context) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(context, "context");
        List<EvalObservation> observations = new ArrayList<>();
        for (EvalCase evalCase : plan.getPlannedCases()) {
            List<EvalLayer> layers = evalCase.getLayers() == null
                    ? List.of() : evalCase.getLayers();
            for (EvalLayer layer : layers) {
                if (layer == EvalLayer.PROVIDER && plan.getMode() != EvalRunMode.PROVIDER) {
                    continue;
                }
                if (layer == EvalLayer.PROVIDER
                        && context.getProviderAuthorization()
                        == com.portfolio.agent.evaluation.domain.EvalProviderAuthorization.NOT_AUTHORIZED) {
                    continue;
                }
                int trials = layer == EvalLayer.PROVIDER
                        ? providerTrials(evalCase) : 1;
                for (int trial = 1; trial <= trials; trial++) {
                    EvalObservation observation =
                            executeOne(evalCase, layer, trial, context);
                    if (observation != null) {
                        observations.add(observation);
                    }
                }
            }
        }
        return List.copyOf(observations);
    }

    private int providerTrials(EvalCase evalCase) {
        if (evalCase.getRiskLevel() == EvalRiskLevel.HIGH
                || evalCase.getRiskLevel() == EvalRiskLevel.INVARIANT) {
            return 3;
        }
        return Math.max(1, evalCase.getProviderTrials());
    }

    private EvalObservation executeOne(
            EvalCase evalCase,
            EvalLayer layer,
            int trialIndex,
            EvalRunContext context) {
        EvalExecutor executor = executors.get(layer);
        if (executor == null) {
            if (layer == EvalLayer.PROVIDER) {
                // Provider layer is intentionally not run when no Provider
                // executor is registered (no explicit authorization).
                return null;
            }
            return error(evalCase.getId(), layer, trialIndex, "EXECUTOR_MISSING");
        }
        // Maintenance metadata belongs to dataset validation only. Passing it
        // to an executor would give the system the expected subject as an
        // oracle and make routing/retrieval results meaningless.
        EvalExecutionInput input = new EvalExecutionInput(
                evalCase.getId(), inputMessages(evalCase), layer, trialIndex);
        try {
            EvalObservation observation = executor.execute(input, context);
            return observation == null
                    ? error(evalCase.getId(), layer, trialIndex, "EXECUTOR_ERROR")
                    : observation;
        } catch (RuntimeException failure) {
            return error(evalCase.getId(), layer, trialIndex, "EXECUTOR_ERROR");
        }
    }

    private List<EvalMessage> inputMessages(EvalCase evalCase) {
        List<EvalMessage> messages = evalCase.getInputMessages();
        return messages == null ? List.of() : messages;
    }

    private EvalObservation error(
            String caseId,
            EvalLayer layer,
            int trialIndex,
            String reasonCode) {
        return new EvalObservation(
                caseId, layer, trialIndex, EvalObservationStatus.ERROR,
                null, null, List.of(), List.of(), List.of(),
                AnswerResolution.CAPABILITY_UNAVAILABLE, ConversationAnswerScope.PORTFOLIO,
                GenerationMode.DETERMINISTIC, AnswerSource.RETRIEVAL,
                List.of(reasonCode), 0L, EvalProviderUsage.unavailable(),
                EvalAnswerShape.empty(), false, false);
    }
}
