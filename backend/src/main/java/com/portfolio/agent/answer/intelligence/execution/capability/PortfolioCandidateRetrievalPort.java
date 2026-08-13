package com.portfolio.agent.answer.intelligence.execution.capability;

import com.portfolio.agent.answer.intelligence.execution.domain.CapabilityExecutionConstraints;
import com.portfolio.agent.answer.intelligence.execution.domain.PortfolioEvidenceInvocation;

/** The only retrieval seam visible to the P3 capability. */
public interface PortfolioCandidateRetrievalPort {
    CapabilityExecutionResult retrieve(
            PortfolioEvidenceInvocation invocation, CapabilityExecutionConstraints constraints);

    default CapabilityExecutionResult retrieve(
            PortfolioEvidenceInvocation invocation, CapabilityExecutionConstraints constraints,
            int attempt) {
        return retrieve(invocation, constraints);
    }
}
