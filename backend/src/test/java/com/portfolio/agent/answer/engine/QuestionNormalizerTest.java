package com.portfolio.agent.answer.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionNormalizerTest {

    private final QuestionNormalizer normalizer = new QuestionNormalizer();

    @Test
    void normalizesUnicodeWhitespaceCaseAndTrailingPunctuation() {
        assertThat(normalizer.normalize("  请介绍 ＳＱＬ  Audit 项目？！  "))
                .isEqualTo("请介绍 sql audit 项目");
    }

    @Test
    void keepsMeaningfulInternalPunctuation() {
        assertThat(normalizer.normalize("项目：背景、职责是什么？"))
                .isEqualTo("项目:背景、职责是什么");
    }

    @Test
    void preservesTokenBoundaries() {
        assertThat(normalizer.normalize("SQL A"))
                .isNotEqualTo(normalizer.normalize("SQLA"));
    }
}
