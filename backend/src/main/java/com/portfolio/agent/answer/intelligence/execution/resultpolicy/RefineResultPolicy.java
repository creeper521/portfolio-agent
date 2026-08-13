package com.portfolio.agent.answer.intelligence.execution.resultpolicy;

import com.portfolio.agent.answer.domain.PortfolioAnswerMaterial;
import com.portfolio.agent.answer.intelligence.execution.support.EvidenceSupportAssessment;
import com.portfolio.agent.answer.intelligence.execution.validation.ValidatedEvidenceBundle;

public final class RefineResultPolicy implements PortfolioResultPolicy {
    private final RecommendationResultPolicy delegate;
    public RefineResultPolicy() { this.delegate = new RecommendationResultPolicy(); }
    @Override
    public PortfolioAnswerMaterial material(
            ValidatedEvidenceBundle bundle, EvidenceSupportAssessment assessment, String title) {
        return delegate.material(bundle, assessment, title);
    }
}
