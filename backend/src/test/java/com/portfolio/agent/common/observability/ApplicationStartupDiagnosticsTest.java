package com.portfolio.agent.common.observability;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

class ApplicationStartupDiagnosticsTest {

    @Test
    void publishesClosedSafeContentBundleAndEmbeddingModelEvents() {
        List<DiagnosticEvent> events = new ArrayList<>();
        ApplicationStartupDiagnostics diagnostics = diagnostics(events::add);

        diagnostics.contentBundleLoaded("3.0", "2026-07-27.1", true, 81, 512, 42);
        diagnostics.embeddingModelLoaded(512, 750);

        assertThat(events).hasSize(2);
        assertThat(events.get(0).getName()).isEqualTo("content.bundle.loaded");
        assertThat(events.get(0).getLevel()).isEqualTo(DiagnosticLevel.INFO);
        assertThat(events.get(0).getFields()).containsExactlyInAnyOrderEntriesOf(
                java.util.Map.of(
                        "schema.version", "3.0",
                        "content.version", "2026-07-27.1",
                        "retrieval.enabled", true,
                        "document.count", 81,
                        "vector.dimension", 512,
                        "duration.bucket", "LT_100_MS"));
        assertThat(events.get(1).getName()).isEqualTo("embedding.model.loaded");
        assertThat(events.get(1).getLevel()).isEqualTo(DiagnosticLevel.INFO);
        assertThat(events.get(1).getFields()).containsExactlyInAnyOrderEntriesOf(
                java.util.Map.of(
                        "vector.dimension", 512,
                        "duration.bucket", "FROM_500_TO_1999_MS"));
    }

    @Test
    void failureEventsExposeOnlyStableClosedCodes() {
        List<DiagnosticEvent> events = new ArrayList<>();
        ApplicationStartupDiagnostics diagnostics = diagnostics(events::add);

        diagnostics.contentBundleFailed();
        diagnostics.embeddingModelFailed();

        assertThat(events).extracting(DiagnosticEvent::getName)
                .containsExactly("application.startup.failed", "embedding.model.failed");
        assertThat(events).allSatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(DiagnosticLevel.ERROR);
            assertThat(event.getFields()).containsOnlyKeys("failure.code");
        });
        assertThat(events).extracting(event -> event.getFields().get("failure.code"))
                .containsExactly("CONTENT_BUNDLE_INVALID", "RETRIEVAL_MODEL_LOAD_FAILED");
        assertThat(ApplicationStartupDiagnostics.StartupFailureCode.values())
                .extracting(ApplicationStartupDiagnostics.StartupFailureCode::code)
                .containsExactly(
                        "CONTENT_BUNDLE_INVALID",
                        "RETRIEVAL_MODEL_LOAD_FAILED");
    }

    @Test
    void applicationReadyEventPublishesOnlyApprovedOperationalConfiguration() {
        List<DiagnosticEvent> events = new ArrayList<>();
        ApplicationStartupDiagnostics diagnostics = diagnostics(events::add);

        diagnostics.onApplicationEvent(mock(ApplicationReadyEvent.class));

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getName()).isEqualTo("application.started");
            assertThat(event.getLevel()).isEqualTo(DiagnosticLevel.INFO);
            assertThat(event.getFields()).containsExactlyInAnyOrderEntriesOf(
                    java.util.Map.of(
                            "model_runtime.enabled", true,
                            "model_catalog.selectable_count", 2,
                            "retrieval.profile", "HYBRID",
                            "answer.request_timeout_ms", 12000L,
                            "answer.requests_per_minute", 10,
                            "answer.max_concurrent", 2));
        });
    }

    @Test
    void rejectsAnUnclosedRetrievalProfileAndShieldsStartupFromPublisherFailure() {
        assertThatCode(() -> diagnostics(event -> {
            throw new IllegalStateException("publisher-secret-message");
        }).onApplicationEvent(mock(ApplicationReadyEvent.class)))
                .doesNotThrowAnyException();

        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> new ApplicationStartupDiagnostics(
                        event -> { },
                        true,
                        2,
                        "SENTINEL_MODEL_DIRECTORY",
                        12000,
                        10,
                        2));

        assertThatCode(() -> diagnostics(event -> { })
                .contentBundleLoaded(null, null, true, 1, 512, 1))
                .doesNotThrowAnyException();
    }

    private ApplicationStartupDiagnostics diagnostics(DiagnosticEventPublisher publisher) {
        return new ApplicationStartupDiagnostics(
                publisher,
                true,
                2,
                "HYBRID",
                12000,
                10,
                2);
    }
}
