package com.portfolio.agent.answer.intelligence.execution.resultpolicy;

import com.portfolio.agent.answer.composition.domain.PortfolioAnswerMaterial;
import com.portfolio.agent.answer.intelligence.execution.domain.CandidateSubject;
import com.portfolio.agent.answer.intelligence.execution.support.EvidenceSupportAssessment;
import com.portfolio.agent.answer.intelligence.execution.validation.ValidatedEvidenceBundle;
import com.portfolio.agent.answer.routing.domain.SemanticTask;
import java.util.List;

/** Converts validated support into bounded P1 material. */
public interface PortfolioResultPolicy {
    PortfolioAnswerMaterial material(SemanticTask task, ValidatedEvidenceBundle bundle,
            EvidenceSupportAssessment assessment, List<CandidateSubject> publicSubjects);
}
