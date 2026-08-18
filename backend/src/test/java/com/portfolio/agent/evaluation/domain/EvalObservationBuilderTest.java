package com.portfolio.agent.evaluation.domain;

import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.GenerationMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvalObservationBuilderTest {

    @Test
    void builderDefaultsToNeutralValues() {
        EvalObservation observation = EvalObservation.builder(
                "case-1", EvalLayer.BUNDLE_CONTRACT, 1, EvalObservationStatus.PASS).build();

        assertThat(observation.getCaseId()).isEqualTo("case-1");
        assertThat(observation.getSelectedProjectSlug()).isNull();
        assertThat(observation.getSelectedClaimIds()).isEmpty();
        assertThat(observation.getReasonCodes()).isEmpty();
        assertThat(observation.getDurationMilliseconds()).isZero();
        assertThat(observation.getProviderUsage().isAvailable()).isFalse();
        assertThat(observation.getAnswerShape().getBlockCount()).isZero();
        assertThat(observation.isFallbackUsed()).isFalse();
        assertThat(observation.isProviderInvoked()).isFalse();
    }

    @Test
    void builderCarriesEveryProvidedValue() {
        EvalObservation observation = EvalObservation.builder(
                "case-2", EvalLayer.INTELLIGENCE, 3, EvalObservationStatus.PASS)
                .selectedProjectSlug("sql-audit")
                .selectedClaimIds(List.of("claim-1"))
                .selectedEvidenceIds(List.of("E-01"))
                .resolution(AnswerResolution.ANSWERED)
                .answerScope(ConversationAnswerScope.PORTFOLIO)
                .generationMode(GenerationMode.DETERMINISTIC)
                .answerSource(AnswerSource.RETRIEVAL)
                .reasonCodes(List.of("ANSWERED"))
                .durationMilliseconds(42L)
                .providerUsage(EvalProviderUsage.available(1, 2, 3))
                .fallbackUsed(true)
                .providerInvoked(true)
                .build();

        assertThat(observation.getSelectedProjectSlug()).isEqualTo("sql-audit");
        assertThat(observation.getSelectedClaimIds()).containsExactly("claim-1");
        assertThat(observation.getSelectedEvidenceIds()).containsExactly("E-01");
        assertThat(observation.getResolution()).isEqualTo(AnswerResolution.ANSWERED);
        assertThat(observation.getDurationMilliseconds()).isEqualTo(42L);
        assertThat(observation.getProviderUsage().getInputTokens()).isEqualTo(1);
        assertThat(observation.isFallbackUsed()).isTrue();
        assertThat(observation.isProviderInvoked()).isTrue();
    }

    @Test
    void builderStillValidatesInvariantsThroughTheConstructor() {
        assertThatThrownBy(() -> EvalObservation.builder(
                "case-3", EvalLayer.BUNDLE_CONTRACT, 0, EvalObservationStatus.PASS).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EvalObservation.builder(
                null, EvalLayer.BUNDLE_CONTRACT, 1, EvalObservationStatus.PASS).build())
                .isInstanceOf(NullPointerException.class);
    }
}


