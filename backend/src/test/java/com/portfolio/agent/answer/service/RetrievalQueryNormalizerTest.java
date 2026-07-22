package com.portfolio.agent.answer.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalQueryNormalizerTest {

    private final RetrievalQueryNormalizer normalizer = new RetrievalQueryNormalizer();

    @Test
    void returnsDeterministicLocalTextAndTerms() {
        NormalizedRetrievalQuery query = normalizer.normalize("  ＳＱＬ Audit，完成交付？ ");

        assertThat(query.getLocalText()).isEqualTo("sql audit,完成交付?");
        assertThat(query.getTerms()).containsExactly(
                "sql", "audit", "完成", "成交", "交付");
    }
}
