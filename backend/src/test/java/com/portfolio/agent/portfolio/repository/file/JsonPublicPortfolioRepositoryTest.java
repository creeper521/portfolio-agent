package com.portfolio.agent.portfolio.repository.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.portfolio.domain.ContributionType;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.ProjectStatus;
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

        PortfolioSnapshot snapshot = repository.getSnapshot();
        ProjectProfile project = snapshot.getProjects().getFirst();

        assertThat(snapshot.getSchemaVersion()).isEqualTo("1.0");
        assertThat(project.getSlug()).isEqualTo("sql-audit");
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.DELIVERED);
        assertThat(project.getContributionType()).isEqualTo(ContributionType.PRIMARY);
    }
}
