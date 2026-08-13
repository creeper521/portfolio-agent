package com.portfolio.agent.answer.intelligence.execution.resultpolicy;

import com.portfolio.agent.answer.composition.domain.PortfolioAnswerMaterial;
import com.portfolio.agent.answer.intelligence.execution.domain.CandidateSubject;
import com.portfolio.agent.answer.intelligence.execution.support.EvidenceSupportAssessment;
import com.portfolio.agent.answer.intelligence.execution.validation.ValidatedEvidenceBundle;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import java.util.List;

public final class RefineResultPolicy implements PortfolioResultPolicy {
    private final RecommendationResultPolicy delegate;
    public RefineResultPolicy() { this.delegate = new RecommendationResultPolicy(); }
    @Override
    public PortfolioAnswerMaterial material(SemanticTask task, ValidatedEvidenceBundle bundle,
            EvidenceSupportAssessment assessment, List<CandidateSubject> publicSubjects) {
        return delegate.material(task, bundle, assessment, publicSubjects);
    }
}
