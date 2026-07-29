package com.portfolio.agent.answer.adapter.retrieval;

import com.portfolio.agent.answer.domain.RetrievalCapability;
import com.portfolio.agent.answer.domain.RetrievalMode;
import com.portfolio.agent.answer.service.LocalEmbeddingFailureException;
import com.portfolio.agent.common.observability.ApplicationStartupDiagnostics;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetrievalConfigurationTest {

    private final RetrievalConfiguration configuration = new RetrievalConfiguration();

    @Test
    void defaultProfileKeepsGroundedQuestionsClosedWithoutLoadingAModel() {
        RetrievalProperties properties = new RetrievalProperties();

        RetrievalCapability capability = configuration.retrievalCapability(
                properties, diagnostics(new ArrayList<>()));

        assertThat(capability.isGroundedQuestionsEnabled()).isFalse();
        assertThat(capability.getMode()).isEqualTo(RetrievalMode.KEYWORD_ONLY);
        assertThatThrownBy(() -> configuration.localEmbeddingPort(
                        properties, diagnostics(new ArrayList<>())).embedQuery("local"))
                .isInstanceOf(LocalEmbeddingFailureException.class)
                .hasMessage("LOCAL_EMBEDDING_DISABLED");
    }

    @Test
    void explicitDevelopmentKeywordProfileNeverRequiresTheOnnxModel() {
        RetrievalProperties properties = new RetrievalProperties();
        properties.setProfile(RetrievalProfile.KEYWORD_ONLY);

        RetrievalCapability capability = configuration.retrievalCapability(
                properties, diagnostics(new ArrayList<>()));

        assertThat(capability.isGroundedQuestionsEnabled()).isTrue();
        assertThat(capability.getMode()).isEqualTo(RetrievalMode.KEYWORD_ONLY);
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

    @Test
    void descriptorFailurePublishesExactlyOneStableFailureAndRethrowsSameException() {
        RetrievalProperties properties = new RetrievalProperties();
        properties.setProfile(RetrievalProfile.HYBRID);
        List<DiagnosticEvent> events = new ArrayList<>();
        LocalEmbeddingArtifactVerifier verifier = mock(LocalEmbeddingArtifactVerifier.class);
        LocalEmbeddingFailureException descriptorFailure =
                new LocalEmbeddingFailureException("LOCAL_MODEL_DESCRIPTOR_INVALID");
        when(verifier.descriptor()).thenThrow(descriptorFailure);

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(() ->
                configuration.retrievalCapability(
                        properties,
                        diagnostics(events),
                        verifier));

        assertThat(thrown).isSameAs(descriptorFailure);
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getName()).isEqualTo("embedding.model.failed");
            assertThat(event.getFields())
                    .containsOnlyKeys("failure.code")
                    .containsEntry(
                            "failure.code",
                            "RETRIEVAL_MODEL_LOAD_FAILED");
        });
    }

    private ApplicationStartupDiagnostics diagnostics(List<DiagnosticEvent> events) {
        return new ApplicationStartupDiagnostics(
                events::add, false, false, "HYBRID", 12000, 10, 2);
    }
}
