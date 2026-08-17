package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.TextAnchor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceMatchPolicyTest {

    private final ReferenceMatchPolicy policy = new ReferenceMatchPolicy();

    @Test
    void doesNotTreatAnAsciiAliasInsideALongerTokenAsAnIndependentReference() {
        assertThat(policy.matches(new TextAnchor("SQL", 1), "MySQL", "SQL")).isFalse();
        assertThat(policy.matches(new TextAnchor("SQL", 1), "SQL 项目", "SQL")).isTrue();
    }
}
