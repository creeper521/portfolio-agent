package com.portfolio.agent.portfolio.repository.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.common.observability.ApplicationStartupDiagnostics;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import com.portfolio.agent.portfolio.domain.ContributionType;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.ProjectStatus;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.domain.TimelineEvent;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class JsonPublicPortfolioRepositoryTest {

    @Test
    void loadedReleaseBundlePublishesValidatedRetrievalMetadata() {
        List<DiagnosticEvent> events = new ArrayList<>();

        JsonPublicPortfolioRepository repository = new JsonPublicPortfolioRepository(
                new ObjectMapper().findAndRegisterModules(),
                bundleResource("manifest.json"),
                bundleResource("portfolio.json"),
                bundleResource("presentation.json"),
                bundleResource("rag-documents.jsonl"),
                bundleResource("keyword-index.json"),
                bundleResource("vector-index.bin"),
                bundleResource("checksums.json"),
                "",
                new PortfolioSnapshotValidator(),
                diagnostics(events));

        assertThat(repository.getSnapshot().getRetrievalContent()).isPresent();
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getName()).isEqualTo("content.bundle.loaded");
            assertThat(event.getFields())
                    .containsEntry("schema.version", "4.0")
                    .containsEntry("content.version", "2026-08-05.1")
                    .containsEntry("retrieval.enabled", true)
                    .containsEntry("document.count", 88)
                    .containsEntry("vector.dimension", 512)
                    .containsKey("duration.bucket");
        });
    }

    @Test
    void invalidReleaseRootPublishesOnlyClosedFailureCodeWithoutThePath() {
        String releaseRootSentinel = "C:/private/release-root-sentinel";
        List<DiagnosticEvent> events = new ArrayList<>();
        Resource unused = mock(Resource.class);

        assertThatThrownBy(() -> new JsonPublicPortfolioRepository(
                new ObjectMapper().findAndRegisterModules(),
                unused,
                unused,
                unused,
                unused,
                unused,
                unused,
                unused,
                releaseRootSentinel,
                new PortfolioSnapshotValidator(),
                diagnostics(events)))
                .isInstanceOf(RuntimeException.class);

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getName()).isEqualTo("application.startup.failed");
            assertThat(event.getFields())
                    .containsOnlyKeys("failure.code")
                    .containsEntry("failure.code", "CONTENT_BUNDLE_INVALID");
            assertThat(event.getFields().toString()).doesNotContain(releaseRootSentinel);
        });
    }

    private ApplicationStartupDiagnostics diagnostics(List<DiagnosticEvent> events) {
        return new ApplicationStartupDiagnostics(
                events::add, false, 0, "DISABLED", 12000, 10, 2);
    }

    private Resource bundleResource(String name) {
        return new ClassPathResource("public-data/bundle/" + name);
    }
}
