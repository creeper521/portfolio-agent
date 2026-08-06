package com.portfolio.agent.common.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StableQuestionNormalizerTest {

    @Test
    void normalizesUnicodeWhitespaceCaseAndTrailingPunctuation() {
        assertThat(StableQuestionNormalizer.normalize("  请介绍 ＳＱＬ  Audit 项目？！  "))
                .isEqualTo("请介绍 sql audit 项目");
    }

    @Test
    void keepsMeaningfulInternalPunctuation() {
        assertThat(StableQuestionNormalizer.normalize("项目：背景、职责是什么？"))
                .isEqualTo("项目:背景、职责是什么");
    }

    @Test
    void preservesTokenBoundaries() {
        assertThat(StableQuestionNormalizer.normalize("SQL A"))
                .isNotEqualTo(StableQuestionNormalizer.normalize("SQLA"));
    }
}
