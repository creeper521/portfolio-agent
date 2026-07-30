package com.portfolio.agent.selection.gateway;

import com.portfolio.agent.selection.domain.CandidateRetrievalResult;
import com.portfolio.agent.selection.domain.SelectionTarget;

@FunctionalInterface
public interface CandidateRetrievalPort {

    CandidateRetrievalResult retrieve(SelectionTarget target, int limit);
}
