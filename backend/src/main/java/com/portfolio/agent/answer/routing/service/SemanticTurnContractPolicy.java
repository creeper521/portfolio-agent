package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.exception.AnswerErrorCode;
import com.portfolio.agent.common.exception.ApplicationException;

/** Central compatibility policy for the semantic turn contract. */
public final class SemanticTurnContractPolicy {

    public static final String COMPATIBILITY_CONTRACT = "stp-v1";
    public static final String CURRENT_CONTRACT = "stp-v2";

    public String resolve(String requestedContract) {
        String normalized = requestedContract == null || requestedContract.isBlank()
                ? COMPATIBILITY_CONTRACT : requestedContract.trim();
        if (!isSupported(normalized)) {
            throw unsupported();
        }
        return normalized;
    }

    public void requireCompatible(String requestedContract, boolean p5SemanticsRequired) {
        String resolved = resolve(requestedContract);
        if (p5SemanticsRequired && COMPATIBILITY_CONTRACT.equals(resolved)) {
            throw unsupported();
        }
    }

    public boolean isSupported(String contract) {
        return COMPATIBILITY_CONTRACT.equals(contract) || CURRENT_CONTRACT.equals(contract);
    }

    private ApplicationException unsupported() {
        return new ApplicationException(
                AnswerErrorCode.AGENT_TURN_CONTRACT_UNSUPPORTED,
                AnswerErrorCode.AGENT_TURN_CONTRACT_UNSUPPORTED.getDefaultMessage());
    }
}
