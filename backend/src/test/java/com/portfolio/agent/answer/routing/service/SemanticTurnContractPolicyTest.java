package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.exception.AnswerErrorCode;
import com.portfolio.agent.common.exception.ApplicationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticTurnContractPolicyTest {

    private final SemanticTurnContractPolicy policy = new SemanticTurnContractPolicy();

    @Test
    void acceptsBothTheCompatibilityContractAndTheCurrentContract() {
        assertThat(policy.resolve(null)).isEqualTo("stp-v1");
        assertThat(policy.resolve("stp-v1")).isEqualTo("stp-v1");
        assertThat(policy.resolve("stp-v2")).isEqualTo("stp-v2");
    }

    @Test
    void rejectsAnUnknownContractWithThePublicConflictCode() {
        assertThatThrownBy(() -> policy.resolve("stp-v9"))
                .isInstanceOf(ApplicationException.class)
                .satisfies(error -> {
                    ApplicationException exception = (ApplicationException) error;
                    assertThat(exception.getErrorCode()).isEqualTo(
                            AnswerErrorCode.AGENT_TURN_CONTRACT_UNSUPPORTED);
                    assertThat(exception.getErrorCode().getHttpStatus()).isEqualTo(409);
                });
    }

    @Test
    void rejectsP5OnlySemanticsWhenTheClientStillUsesV1() {
        assertThatThrownBy(() -> policy.requireCompatible("stp-v1", true))
                .isInstanceOf(ApplicationException.class)
                .extracting(error -> ((ApplicationException) error).getErrorCode())
                .isEqualTo(AnswerErrorCode.AGENT_TURN_CONTRACT_UNSUPPORTED);
        policy.requireCompatible("stp-v2", true);
    }
}
