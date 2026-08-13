package com.portfolio.agent.answer.composition.service;

import com.portfolio.agent.answer.composition.domain.ExpressionAllowance;
import com.portfolio.agent.answer.composition.domain.ExpressionIntent;
import com.portfolio.agent.answer.composition.domain.LocaleCode;
import com.portfolio.agent.answer.composition.domain.MaterialKind;
import com.portfolio.agent.answer.composition.domain.PortfolioAnswerMaterial;
import com.portfolio.agent.answer.composition.domain.TaskKind;
import com.portfolio.agent.answer.composition.domain.TaskSource;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class ModelExpressionEligibilityPolicy {
    public enum Decision { ELIGIBLE, DISABLED, INELIGIBLE, NO_ALLOWANCE, DEADLINE, INPUT_LIMIT }
    private static final Duration MINIMUM_WINDOW = Duration.ofMillis(1500);
    public Decision evaluate(boolean enabled, PortfolioAnswerMaterial material,
            ExpressionIntent intent, ExpressionAllowance allowance, int serializedInputSize, Instant now) {
        Objects.requireNonNull(material); Objects.requireNonNull(intent); Objects.requireNonNull(allowance); Objects.requireNonNull(now);
        if (!enabled) return Decision.DISABLED;
        if (material.getMaterialKind() != MaterialKind.FACT || intent.getTaskKind() != TaskKind.FACT
                || intent.getLocale() != LocaleCode.ZH_CN
                || intent.getTaskSource() == TaskSource.PRESET
                || intent.getSubjectDisplayLabels().size() != 1) return Decision.INELIGIBLE;
        if (!allowance.isAttemptAllowed()) return Decision.NO_ALLOWANCE;
        if (allowance.getAbsoluteDeadline().minus(MINIMUM_WINDOW).isBefore(now)) return Decision.DEADLINE;
        if (serializedInputSize > 12000) return Decision.INPUT_LIMIT;
        return Decision.ELIGIBLE;
    }
}
