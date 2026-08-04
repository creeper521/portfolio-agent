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
    void loadsReviewedPublicSnapshotFromClasspath() {
        List<DiagnosticEvent> events = new ArrayList<>();
        JsonPublicPortfolioRepository repository = new JsonPublicPortfolioRepository(
                new ObjectMapper().findAndRegisterModules(),
                new ClassPathResource("public-data/public-portfolio.v1.json"),
                new PortfolioSnapshotValidator(),
                diagnostics(events)
        );

        RuntimeContentSnapshot snapshot = repository.getSnapshot();
        ProjectProfile project = snapshot.getProjects().getFirst();

        assertThat(snapshot.getSchemaVersion()).isEqualTo("2.0");
        assertThat(snapshot.getCases()).isEmpty();
        assertThat(snapshot.getClaims()).singleElement()
                .satisfies(claim -> assertThat(claim.getId())
                        .isEqualTo("claim-sql-audit-delivered"));
        assertThat(snapshot.getClaimEvidenceLinks()).singleElement()
                .satisfies(link -> assertThat(link.getClaimId())
                        .isEqualTo("claim-sql-audit-delivered"));
        assertThat(snapshot.getRuntimeBundleHash()).startsWith("sha256:");
        assertThat(snapshot.getLoadedAt()).isNotNull();
        assertThat(repository.getSnapshot()).isSameAs(snapshot);
        assertThat(project.getCode()).isEqualTo("P-01");
        assertThat(project.getSlug()).isEqualTo("sql-audit");
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.DELIVERED);
        assertThat(project.getContributionType()).isEqualTo(ContributionType.PRIMARY);
        assertThat(snapshot.getEvidence().getFirst().getCode()).isEqualTo("E-01");
        assertThat(snapshot.getTimeline()).singleElement()
                .extracting(TimelineEvent::getId)
                .isEqualTo("timeline-sql-audit-delivery");
        assertThat(snapshot.getQuestionPresets()).singleElement()
                .satisfies(preset -> {
                    assertThat(preset.getId()).isEqualTo("sql-audit-overview");
                    assertThat(preset.isDeterministicEntry()).isTrue();
                    assertThat(preset.getAudiences())
                            .containsExactly("INTERVIEWER", "MENTOR", "HR", "GUEST");
                });
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getName()).isEqualTo("content.bundle.loaded");
            assertThat(event.getFields())
                    .containsEntry("schema.version", "2.0")
                    .containsEntry("content.version", snapshot.getContentVersion())
                    .containsEntry("retrieval.enabled", false)
                    .containsEntry("document.count", 0)
                    .containsEntry("vector.dimension", 0)
                    .containsKey("duration.bucket");
        });
    }

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
                    .containsEntry("content.version", "2026-08-04.1")
                    .containsEntry("retrieval.enabled", true)
                    .containsEntry("document.count", 79)
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
                events::add, false, false, "DISABLED", 12000, 10, 2);
    }

    private Resource bundleResource(String name) {
        return new ClassPathResource("public-data/bundle/" + name);
    }
}
