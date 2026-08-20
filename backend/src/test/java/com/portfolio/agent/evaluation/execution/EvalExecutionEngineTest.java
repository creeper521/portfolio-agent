package com.portfolio.agent.evaluation.execution;

import static org.assertj.core.api.Assertions.assertThat;
import com.portfolio.agent.evaluation.domain.AnswerResolution;
import com.portfolio.agent.evaluation.domain.AnswerSource;
import com.portfolio.agent.evaluation.domain.ConversationAnswerScope;
import com.portfolio.agent.common.observability.GenerationMode;
import com.portfolio.agent.evaluation.domain.EvalCase;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalMessage;
import com.portfolio.agent.evaluation.domain.EvalObservation;
import com.portfolio.agent.evaluation.domain.EvalObservationStatus;
import com.portfolio.agent.evaluation.domain.EvalOrigin;
import com.portfolio.agent.evaluation.domain.EvalRiskLevel;
import com.portfolio.agent.evaluation.domain.EvalRunMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvalExecutionEngineTest {

    @Test
    void offlineSkipsHttpLayerButExecutesBundleContractInMixedCase() {
        EvalExecutor bundleContract = new EvalExecutor() {
            @Override
            public boolean supports(EvalLayer layer) {
                return layer == EvalLayer.BUNDLE_CONTRACT;
            }

            @Override
            public EvalObservation execute(
                    EvalExecutionInput input, EvalRunContext context) {
                return EvalObservation.builder(
                                input.getCaseId(), input.getLayer(), input.getTrialIndex(),
                                EvalObservationStatus.PASS)
                        .resolution(AnswerResolution.ANSWERED)
                        .answerScope(ConversationAnswerScope.PORTFOLIO)
                        .generationMode(GenerationMode.DETERMINISTIC)
                        .answerSource(AnswerSource.RETRIEVAL)
                        .reasonCodes(List.of("ANSWERED"))
                        .build();
            }
        };
        EvalExecutionEngine engine = new EvalExecutionEngine(List.of(bundleContract));
        EvalRunPlan plan = new EvalRunPlan(
                EvalRunMode.OFFLINE,
                List.of(evalCase("mixed", List.of(EvalLayer.BUNDLE_CONTRACT, EvalLayer.HTTP_E2E)),
                        evalCase("http-only", List.of(EvalLayer.HTTP_E2E))),
                false,
                true);

        List<EvalObservation> observations = engine.execute(
                plan, new EvalRunContext(
                        "run-1", "public-1",
                        com.portfolio.agent.evaluation.domain.EvalProviderAuthorization
                                .NOT_AUTHORIZED));

        assertThat(observations).singleElement().satisfies(observation -> {
            assertThat(observation.getCaseId()).isEqualTo("mixed");
            assertThat(observation.getLayer()).isEqualTo(EvalLayer.BUNDLE_CONTRACT);
            assertThat(observation.getStatus()).isEqualTo(EvalObservationStatus.PASS);
        });
    }

    private EvalCase evalCase(String id, List<EvalLayer> layers) {
        return new EvalCase(
                id, id, com.portfolio.agent.evaluation.domain.EvalSplit.HOLDOUT,
                EvalOrigin.HUMAN_AUTHORED, EvalRiskLevel.STANDARD, "APPROVED", "test",
                "TEST", "test", "2026-08-10", List.of("test"),
                new EvalCase.Input(List.of(new EvalMessage("user", "test"))),
                new EvalCase.Oracle(List.of()),
                new EvalCase.Expectations(List.of(AnswerResolution.ANSWERED),
                        List.of(ConversationAnswerScope.PORTFOLIO), List.of(), List.of(),
                        List.of(), List.of()),
                new EvalCase.Execution(layers, 1), List.of(),
                new EvalCase.Maintenance(List.of(), false));
    }
}
