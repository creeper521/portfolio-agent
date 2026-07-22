package com.portfolio.agent.answer.adapter.retrieval;

import com.portfolio.agent.answer.domain.RetrievalCapability;
import com.portfolio.agent.answer.domain.RetrievalMode;
import com.portfolio.agent.answer.service.LocalEmbeddingFailureException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetrievalConfigurationTest {

    private final RetrievalConfiguration configuration = new RetrievalConfiguration();

    @Test
    void defaultProfileKeepsGroundedQuestionsClosedWithoutLoadingAModel() {
        RetrievalProperties properties = new RetrievalProperties();

        RetrievalCapability capability = configuration.retrievalCapability(properties);

        assertThat(capability.isGroundedQuestionsEnabled()).isFalse();
        assertThat(capability.getMode()).isEqualTo(RetrievalMode.KEYWORD_ONLY);
        assertThatThrownBy(() -> configuration.localEmbeddingPort(properties).embedQuery("local"))
                .isInstanceOf(LocalEmbeddingFailureException.class)
                .hasMessage("LOCAL_EMBEDDING_DISABLED");
    }

    @Test
    void explicitDevelopmentKeywordProfileNeverRequiresTheOnnxModel() {
        RetrievalProperties properties = new RetrievalProperties();
        properties.setProfile(RetrievalProfile.KEYWORD_ONLY);

        RetrievalCapability capability = configuration.retrievalCapability(properties);

        assertThat(capability.isGroundedQuestionsEnabled()).isTrue();
        assertThat(capability.getMode()).isEqualTo(RetrievalMode.KEYWORD_ONLY);
    }
}
