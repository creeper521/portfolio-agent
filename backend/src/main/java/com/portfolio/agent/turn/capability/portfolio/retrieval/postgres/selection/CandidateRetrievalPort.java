package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection;

import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.CandidateRetrievalResult;
import com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection.SelectionTarget;

@FunctionalInterface
public interface CandidateRetrievalPort {

    CandidateRetrievalResult retrieve(SelectionTarget target, int limit);
}
