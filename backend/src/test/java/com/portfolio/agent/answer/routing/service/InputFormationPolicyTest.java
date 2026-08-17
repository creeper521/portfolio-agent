package com.portfolio.agent.answer.routing.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class InputFormationPolicyTest {

    private final InputFormationPolicy policy = new InputFormationPolicy();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "1", "112233", "!!!", "😀", "--__--"})
    void classifiesInputWithoutNaturalLanguageCharactersAsUnformed(String question) {
        assertThat(policy.evaluate(question)).isEqualTo(InputFormationPolicy.Formation.UNFORMED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"推荐两个项目", "project recommendation", "介绍 SQL 项目"})
    void classifiesInputWithNaturalLanguageCharactersAsFormed(String question) {
        assertThat(policy.evaluate(question)).isEqualTo(InputFormationPolicy.Formation.FORMED);
    }
}
