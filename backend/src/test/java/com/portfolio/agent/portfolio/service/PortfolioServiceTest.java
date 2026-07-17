package com.portfolio.agent.portfolio.service;

import com.portfolio.agent.portfolio.domain.ContributionType;
import com.portfolio.agent.portfolio.domain.EvidenceRecord;
import com.portfolio.agent.portfolio.domain.EvidenceStatus;
import com.portfolio.agent.portfolio.domain.EvidenceType;
import com.portfolio.agent.portfolio.domain.OwnerProfile;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.ProjectStatus;
import com.portfolio.agent.portfolio.domain.QuestionDefinition;
import com.portfolio.agent.portfolio.domain.TimelineEvent;
import com.portfolio.agent.portfolio.repository.PublicPortfolioRepository;
import com.portfolio.agent.portfolio.service.result.PortfolioOverview;
import com.portfolio.agent.portfolio.service.result.PublicContent;
import com.portfolio.agent.portfolio.service.result.ProjectDetails;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioServiceTest {

    @Test
    void getProjectReadsExactlyOneSnapshot() {
        CountingRepository repository = new CountingRepository(snapshot());
        PortfolioService service = new PortfolioService(repository);

        ProjectDetails details = service.getProject("sql-audit");

        assertThat(details.getProject().getSlug()).isEqualTo("sql-audit");
        assertThat(repository.reads).isEqualTo(1);
    }

    @Test
    void getPortfolioReturnsApplicationModelInsteadOfResponseDto() {
        PortfolioService service = new PortfolioService(new CountingRepository(snapshot()));

        PortfolioOverview overview = service.getPortfolio();

        assertThat(overview.getContentVersion()).isEqualTo("2026-07-14.1");
        assertThat(overview.getProjects()).hasSize(1);
    }

    @Test
    void getPublicContentReadsOneSnapshotAndBuildsReverseEvidenceLinks() {
        CountingRepository repository = new CountingRepository(snapshot());
        PortfolioService service = new PortfolioService(repository);

        PublicContent content = service.getPublicContent();

        assertThat(repository.reads).isEqualTo(1);
        assertThat(content.getProjects()).singleElement()
                .satisfies(project -> assertThat(project.getSuggestedQuestions())
                        .containsExactly("What did you build?"));
        assertThat(content.getEvidence()).extracting(EvidenceRecord::getId)
                .containsExactly("evidence-1");
        assertThat(content.getProjectSlugsByEvidenceId().get("evidence-1"))
                .containsExactly("sql-audit");
        assertThat(content.getTimeline()).extracting(TimelineEvent::getId)
                .containsExactly("timeline-1");
    }

    private static PortfolioSnapshot snapshot() {
        OwnerProfile owner = new OwnerProfile(
                "Owner",
                "Java Developer",
                "Summary",
                "https://github.com/example",
                "owner@example.com",
                "/resume.pdf"
        );
        ProjectProfile project = new ProjectProfile(
                "project-1",
                "P-01",
                "sql-audit",
                "SQL Audit",
                "Summary",
                "Background",
                List.of("Responsibility"),
                "Solution",
                List.of("Decision"),
                List.of("Java"),
                List.of("Tested"),
                "Outcome",
                "Handoff",
                ProjectStatus.DELIVERED,
                ContributionType.PRIMARY,
                List.of("question-1"),
                List.of("evidence-1")
        );
        QuestionDefinition question = new QuestionDefinition(
                "question-1",
                "project-1",
                "What did you build?",
                List.of("Explain the project"),
                "What did you build?"
        );
        EvidenceRecord evidence = new EvidenceRecord(
                "evidence-1",
                "E-01",
                "Delivery evidence",
                EvidenceType.DOCUMENT,
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-14"),
                1,
                "Approved public evidence",
                List.of("Delivered"),
                EvidenceStatus.APPROVED,
                false
        );
        TimelineEvent timeline = new TimelineEvent(
                "timeline-1",
                "2026.06–07",
                "Delivery loop",
                "Hard-coded paths",
                "Completed multi-target routing",
                "Created a deliverable version",
                List.of("sql-audit"),
                List.of("evidence-1")
        );
        return new PortfolioSnapshot(
                "1.0",
                "2026-07-14.1",
                OffsetDateTime.parse("2026-07-14T12:00:00+08:00"),
                owner,
                List.of(project),
                List.of(question),
                List.of(evidence),
                List.of(timeline)
        );
    }

    private static final class CountingRepository implements PublicPortfolioRepository {

        private final PortfolioSnapshot snapshot;
        private int reads;

        private CountingRepository(PortfolioSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public PortfolioSnapshot getSnapshot() {
            reads++;
            return snapshot;
        }
    }
}
