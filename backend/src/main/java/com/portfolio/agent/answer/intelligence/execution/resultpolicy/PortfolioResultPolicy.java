package com.portfolio.agent.answer.intelligence.execution.resultpolicy;

import com.portfolio.agent.answer.domain.PortfolioAnswerMaterial;
import com.portfolio.agent.answer.intelligence.execution.support.EvidenceSupportAssessment;
import com.portfolio.agent.answer.intelligence.execution.validation.ValidatedEvidenceBundle;

/** Converts validated support into bounded P1 material. */
public interface PortfolioResultPolicy {
    PortfolioAnswerMaterial material(
            ValidatedEvidenceBundle bundle, EvidenceSupportAssessment assessment, String title);
}
