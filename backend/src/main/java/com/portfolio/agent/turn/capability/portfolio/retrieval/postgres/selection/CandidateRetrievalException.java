package com.portfolio.agent.turn.capability.portfolio.retrieval.postgres.selection;

/** 候选检索基础设施异常：包装 SQL/驱动层故障，供适配层归类为 RetrievalAttemptFailure。 */
public final class CandidateRetrievalException extends RuntimeException {

    public CandidateRetrievalException(String message) {
        super(message);
    }

    public CandidateRetrievalException(String message, Throwable cause) {
        super(message, cause);
    }
}
