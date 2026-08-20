package com.portfolio.agent.infrastructure.retrieval.adapter;

import com.portfolio.agent.infrastructure.retrieval.LocalEmbeddingPort;
import com.portfolio.agent.infrastructure.retrieval.LocalEmbeddingFailureException;
import com.portfolio.agent.common.observability.ApplicationStartupDiagnostics;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetrievalConfigurationTest {

    private final RetrievalConfiguration configuration = new RetrievalConfiguration();

    @Test
    void defaultProfileDoesNotLoadAnEmbeddingModel() {
        RetrievalProperties properties = new RetrievalProperties();
        assertThatThrownBy(() -> configuration.localEmbeddingPort(
                        properties, diagnostics(new ArrayList<>())).embedQuery("local"))
                .isInstanceOf(LocalEmbeddingFailureException.class)
                .hasMessage("LOCAL_EMBEDDING_DISABLED");
    }

    @Test
    void modelLoadFailurePublishesOnlyStableCodeAndNeverConfiguredDirectory() {
        String modelDirectorySentinel = "C:/private/model-directory-sentinel";
        RetrievalProperties properties = new RetrievalProperties();
        properties.setProfile(RetrievalProfile.HYBRID);
        properties.setModelDirectory(modelDirectorySentinel);
        List<DiagnosticEvent> events = new ArrayList<>();

        assertThatThrownBy(() -> configuration.localEmbeddingPort(
                properties, diagnostics(events)))
                .isInstanceOf(LocalEmbeddingFailureException.class);

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getName()).isEqualTo("embedding.model.failed");
            assertThat(event.getFields())
                    .containsOnlyKeys("failure.code")
                    .containsEntry("failure.code", "RETRIEVAL_MODEL_LOAD_FAILED");
            assertThat(event.getFields().toString()).doesNotContain(modelDirectorySentinel);
        });
    }

    private ApplicationStartupDiagnostics diagnostics(List<DiagnosticEvent> events) {
        return new ApplicationStartupDiagnostics(
                events::add, false, false, "HYBRID", 12000, 10, 2);
    }
}
