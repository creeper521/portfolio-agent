package com.portfolio.agent.answer.adapter.retrieval;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BgeQueryTextFactoryTest {

    @Test
    void prependsThePinnedChineseRetrievalInstruction() {
        BgeQueryTextFactory factory = new BgeQueryTextFactory(
                "为这个句子生成表示以用于检索相关文章：");

        assertThat(factory.prepare("sql audit 交付"))
                .isEqualTo("为这个句子生成表示以用于检索相关文章：sql audit 交付");
    }
}
