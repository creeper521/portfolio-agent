package com.portfolio.agent.answer.composition.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.portfolio.agent.answer.composition.domain.ExpressionAllowance;
import com.portfolio.agent.answer.composition.domain.ExpressionIntent;
import com.portfolio.agent.answer.composition.domain.FactAnswerMaterial;
import com.portfolio.agent.answer.composition.domain.LocaleCode;
import com.portfolio.agent.answer.composition.domain.MaterialKind;
import com.portfolio.agent.answer.composition.domain.PortfolioAnswerMaterial;
import com.portfolio.agent.answer.composition.domain.TaskKind;
import com.portfolio.agent.answer.composition.domain.TaskSource;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelExpressionEligibilityPolicyTest {

    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");
    private final ModelExpressionEligibilityPolicy policy = new ModelExpressionEligibilityPolicy();

    @Test
    void inputLimitIsIndependentFromFinalAnswerCharacterLimit() {
        assertThat(policy.evaluate(true, fact(), intent(), allowance(100), 12_000, NOW))
                .isEqualTo(ModelExpressionEligibilityPolicy.Decision.ELIGIBLE);
        assertThat(policy.evaluate(true, fact(), intent(), allowance(20_000), 12_001, NOW))
                .isEqualTo(ModelExpressionEligibilityPolicy.Decision.INPUT_LIMIT);
    }

    @Test
    void presetAndInsufficientWindowAreNotAttempted() {
        ExpressionIntent preset = intent();
        when(preset.getTaskSource()).thenReturn(TaskSource.PRESET);
        assertThat(policy.evaluate(true, fact(), preset, allowance(4_000), 100, NOW))
                .isEqualTo(ModelExpressionEligibilityPolicy.Decision.INELIGIBLE);
        ExpressionAllowance late = mock(ExpressionAllowance.class);
        when(late.isAttemptAllowed()).thenReturn(true);
        when(late.getAbsoluteDeadline()).thenReturn(NOW.plusMillis(1_499));
        assertThat(policy.evaluate(true, fact(), intent(), late, 100, NOW))
                .isEqualTo(ModelExpressionEligibilityPolicy.Decision.DEADLINE);
    }

    private PortfolioAnswerMaterial fact() {
        FactAnswerMaterial material = mock(FactAnswerMaterial.class);
        when(material.getMaterialKind()).thenReturn(MaterialKind.FACT);
        return material;
    }

    private ExpressionIntent intent() {
        ExpressionIntent intent = mock(ExpressionIntent.class);
        when(intent.getTaskKind()).thenReturn(TaskKind.FACT);
        when(intent.getLocale()).thenReturn(LocaleCode.ZH_CN);
        when(intent.getTaskSource()).thenReturn(TaskSource.FREE_TEXT);
        when(intent.getSubjectDisplayLabels()).thenReturn(List.of("公开项目"));
        return intent;
    }

    private ExpressionAllowance allowance(int characterLimit) {
        ExpressionAllowance allowance = mock(ExpressionAllowance.class);
        when(allowance.isAttemptAllowed()).thenReturn(true);
        when(allowance.getAbsoluteDeadline()).thenReturn(NOW.plusSeconds(10));
        when(allowance.getCharacterLimit()).thenReturn(characterLimit);
        return allowance;
    }
}
