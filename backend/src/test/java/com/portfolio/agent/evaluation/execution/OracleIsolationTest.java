package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.evaluation.domain.EvalAnswerShape;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalObservation;
import com.portfolio.agent.evaluation.domain.EvalObservationStatus;
import com.portfolio.agent.evaluation.domain.EvalProviderUsage;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OracleIsolationTest {

    @Test
    void executionInputCarriesOnlyMessagesLayerTrialsAndResolvedSubjects() {
        // The input boundary admits the resolved subject references needed by
        // deterministic subject-internal retrieval, but never oracle fields.
        assertThat(Arrays.stream(EvalExecutionInput.class.getDeclaredFields())
                .map(Field::getName))
                .containsExactlyInAnyOrder(
                        "caseId", "messages", "layer", "trialIndex", "resolvedSubjects");
        assertThat(Arrays.stream(EvalExecutionInput.class.getDeclaredFields())
                .map(Field::getName))
                .noneMatch(name -> name.contains("oracle")
                        || name.contains("expectation")
                        || name.contains("claim")
                        || name.contains("evidence")
                        || name.contains("prompt")
                        || name.contains("resolution")
                        || name.contains("scope"));
    }

    @Test
    void providerUsageDistinguishesUnavailableFromObservedZero() {
        EvalProviderUsage unavailable = EvalProviderUsage.unavailable();
        EvalProviderUsage observedZero = EvalProviderUsage.available(0, 0, 0);

        assertThat(unavailable.isAvailable()).isFalse();
        assertThat(unavailable.getInputTokens()).isNull();
        assertThat(observedZero.isAvailable()).isTrue();
        assertThat(observedZero.getInputTokens()).isZero();
        assertThat(observedZero.getOutputTokens()).isZero();
        assertThat(observedZero.getTotalTokens()).isZero();
    }

    @Test
    void observationCarriesProviderUsageAsAnExplicitAvailabilityValue() {
        EvalObservation observation = new EvalObservation(
                "answer.safe.001", EvalLayer.INTELLIGENCE, 1, EvalObservationStatus.PASS,
                null, null, List.of(), List.of(), List.of(), AnswerResolution.ANSWERED,
                null, null, null, List.of("DETERMINISTIC"), 12,
                EvalProviderUsage.unavailable(), EvalAnswerShape.empty(), false, true);

        assertThat(observation.getProviderUsage().isAvailable()).isFalse();
        assertThat(observation.getAnswerShape().getBlockCount()).isZero();
        assertThat(observation.isProviderInvoked()).isTrue();
    }

    @Test
    void observationNeverCarriesPromptRawAnswerPathOrExceptionFields() {
        assertThat(Arrays.stream(EvalObservation.class.getDeclaredFields())
                .map(Field::getName))
                .noneMatch(name -> name.contains("prompt")
                        || name.contains("rawAnswer")
                        || name.contains("path")
                        || name.contains("exception")
                        || name.contains("body")
                        || name.contains("question"));
    }
}
