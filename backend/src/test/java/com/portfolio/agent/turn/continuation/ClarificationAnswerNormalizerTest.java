package com.portfolio.agent.turn.continuation;

import com.portfolio.agent.turn.planning.BlockedGoalTemplate;
import com.portfolio.agent.turn.planning.ClarificationProposal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ClarificationAnswerNormalizerTest {
    private final ClarificationAnswerNormalizer normalizer = new ClarificationAnswerNormalizer();

    @Test
    void recommendationConstraintClarificationIsNotAValidBlockedShape() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                BlockedGoalTemplate.recommendation(
                        2, Set.of(), ClarificationProposal.Field.CONSTRAINT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match goal kind");
    }
}
