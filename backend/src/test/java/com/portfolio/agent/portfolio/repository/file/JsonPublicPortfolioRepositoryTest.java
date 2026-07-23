package com.portfolio.agent.portfolio.repository.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.portfolio.domain.ContributionType;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.ProjectStatus;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.domain.TimelineEvent;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class JsonPublicPortfolioRepositoryTest {

    @Test
    void loadsReviewedPublicSnapshotFromClasspath() {
        JsonPublicPortfolioRepository repository = new JsonPublicPortfolioRepository(
                new ObjectMapper().findAndRegisterModules(),
                new ClassPathResource("public-data/public-portfolio.v1.json"),
                new PortfolioSnapshotValidator()
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
    }
}
