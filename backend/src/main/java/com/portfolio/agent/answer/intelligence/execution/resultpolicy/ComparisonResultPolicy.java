package com.portfolio.agent.answer.intelligence.execution.resultpolicy;

import com.portfolio.agent.answer.domain.PortfolioAnswerMaterial;
import com.portfolio.agent.answer.intelligence.execution.support.EvidenceSupportAssessment;
import com.portfolio.agent.answer.intelligence.execution.validation.ValidatedEvidenceBundle;

public final class ComparisonResultPolicy implements PortfolioResultPolicy {
    @Override
    public PortfolioAnswerMaterial material(
            ValidatedEvidenceBundle bundle, EvidenceSupportAssessment assessment, String title) {
        return PortfolioAnswerMaterial.fromContribution(
                PortfolioAnswerMaterial.MaterialKind.COMPARISON, title,
                FactResultPolicy.contributionOf(assessment));
    }
}
