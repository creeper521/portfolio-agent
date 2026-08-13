package com.portfolio.agent.answer.intelligence.execution.capability;

import com.portfolio.agent.answer.intelligence.execution.domain.CapabilityExecutionConstraints;
import com.portfolio.agent.answer.intelligence.execution.domain.PortfolioEvidenceInvocation;

/** Single bounded, read-only Portfolio execution capability. */
public interface PortfolioEvidenceCapability {
    CapabilityExecutionResult execute(
            PortfolioEvidenceInvocation invocation, CapabilityExecutionConstraints constraints);
}
