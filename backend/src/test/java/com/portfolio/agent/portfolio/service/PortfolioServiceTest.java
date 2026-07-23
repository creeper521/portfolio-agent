package com.portfolio.agent.portfolio.service;

import com.portfolio.agent.portfolio.domain.AchievementStatus;
import com.portfolio.agent.portfolio.domain.CaseStudy;
import com.portfolio.agent.portfolio.domain.CaseType;
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
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.exception.CaseNotFoundException;
import com.portfolio.agent.portfolio.exception.PortfolioErrorCode;
import com.portfolio.agent.portfolio.repository.PublicPortfolioRepository;
import com.portfolio.agent.portfolio.service.result.CaseDetails;
import com.portfolio.agent.portfolio.service.result.PortfolioOverview;
import com.portfolio.agent.portfolio.service.result.PublicContent;
import com.portfolio.agent.portfolio.service.result.ProjectDetails;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                .containsExactly(
                        "evidence-1",
                        "evidence-case-multilingual-implementation-and-regression",
                        "evidence-case-evaluation"
                );
        assertThat(content.getProjectSlugsByEvidenceId().get("evidence-1"))
                .containsExactly("sql-audit");
        assertThat(content.getTimeline()).extracting(TimelineEvent::getId)
                .containsExactly("timeline-1");
    }

    @Test
    void getCasesPreservesBundleOrder() {
        PortfolioService service = new PortfolioService(new CountingRepository(snapshot()));

        List<CaseDetails> cases = service.getCases();

        assertThat(cases)
                .extracting(details -> details.getCaseStudy().getSlug())
                .containsExactly(
                        "multilingual-image-preservation",
                        "provider-evaluation"
                );
    }

    @Test
    void returnsCaseDetailsWithOnlyOwnApprovedNonRawEvidenceAndCaseQuestions() {
        PortfolioService service = new PortfolioService(new CountingRepository(snapshot()));

        CaseDetails result = service.getCase("multilingual-image-preservation");

        assertThat(result.getCaseStudy().getCode()).isEqualTo("CASE-01");
        assertThat(result.getEvidence()).extracting(EvidenceRecord::getId)
                .containsExactly("evidence-case-multilingual-implementation-and-regression");
        assertThat(result.getSuggestedQuestions())
                .containsExactly("多语言图片上传修复解决了什么问题？");
    }

    @Test
    void getCaseMatchesExactSlugAndUnknownCaseUsesStableErrorCode() {
        PortfolioService service = new PortfolioService(new CountingRepository(snapshot()));

        assertThatThrownBy(() -> service.getCase("MULTILINGUAL-IMAGE-PRESERVATION"))
                .isInstanceOfSatisfying(
                        CaseNotFoundException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(PortfolioErrorCode.CASE_NOT_FOUND)
                );
    }

    @Test
    void publicContentIncludesCasesAndIndexesOnlyTheirPublicEvidence() {
        PortfolioService service = new PortfolioService(new CountingRepository(snapshot()));

        PublicContent content = service.getPublicContent();

        assertThat(content.getCases())
                .extracting(details -> details.getCaseStudy().getSlug())
                .containsExactly(
                        "multilingual-image-preservation",
                        "provider-evaluation"
                );
        assertThat(content.getCaseSlugsByEvidenceId())
                .containsEntry(
                        "evidence-case-multilingual-implementation-and-regression",
                        List.of("multilingual-image-preservation")
                )
                .containsEntry(
                        "evidence-case-evaluation",
                        List.of("provider-evaluation")
                )
                .doesNotContainKeys(
                        "evidence-case-pending",
                        "evidence-case-raw"
                );
        assertThat(content.getClaimIdsByEvidenceId())
                .doesNotContainKey("evidence-case-multilingual-implementation-and-regression");
    }

    @Test
    void publicContentCaseCollectionsAreDeeplyImmutable() {
        PortfolioService service = new PortfolioService(new CountingRepository(snapshot()));
        PublicContent content = service.getPublicContent();

        assertThatThrownBy(() -> content.getCases().add(content.getCases().get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> content.getCaseSlugsByEvidenceId().put("evidence-new", List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> content.getCaseSlugsByEvidenceId()
                .get("evidence-case-multilingual-implementation-and-regression")
                .add("another-case"))
                .isInstanceOf(UnsupportedOperationException.class);
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
                List.of(),
                List.of("evidence-1"),
                List.of("timeline-1")
        );
        QuestionDefinition question = new QuestionDefinition(
                "question-1",
                "What did you build?",
                List.of("Explain the project"),
                List.of("INTERVIEWER"),
                List.of("project-1"),
                List.of(),
                List.of("OVERVIEW"),
                List.of(com.portfolio.agent.portfolio.domain.ClaimCategory.OUTCOME),
                List.of("HOME"),
                true,
                10
        );
        QuestionDefinition caseQuestion = new QuestionDefinition(
                "question-case-1",
                "多语言图片上传修复解决了什么问题？",
                List.of("Explain the multilingual image fix"),
                List.of("INTERVIEWER"),
                List.of(),
                List.of("case-1"),
                List.of("IMPLEMENTATION"),
                List.of(com.portfolio.agent.portfolio.domain.ClaimCategory.OUTCOME),
                List.of("CASE"),
                true,
                20
        );
        QuestionDefinition otherCaseQuestion = new QuestionDefinition(
                "question-case-2",
                "模型评测如何开展？",
                List.of("Explain the provider evaluation"),
                List.of("INTERVIEWER"),
                List.of(),
                List.of("case-2"),
                List.of("EVALUATION"),
                List.of(com.portfolio.agent.portfolio.domain.ClaimCategory.VERIFICATION),
                List.of("CASE"),
                true,
                30
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
                EvidenceStatus.APPROVED,
                false
        );
        EvidenceRecord caseEvidence = evidence(
                "evidence-case-multilingual-implementation-and-regression",
                EvidenceStatus.APPROVED,
                false
        );
        EvidenceRecord pendingCaseEvidence = evidence(
                "evidence-case-pending",
                EvidenceStatus.PENDING,
                false
        );
        EvidenceRecord rawCaseEvidence = evidence(
                "evidence-case-raw",
                EvidenceStatus.APPROVED,
                true
        );
        EvidenceRecord otherCaseEvidence = evidence(
                "evidence-case-evaluation",
                EvidenceStatus.APPROVED,
                false
        );
        CaseStudy multilingualCase = caseStudy(
                "case-1",
                "CASE-01",
                "multilingual-image-preservation",
                CaseType.FEATURE,
                List.of(
                        caseEvidence.getId(),
                        pendingCaseEvidence.getId(),
                        rawCaseEvidence.getId()
                )
        );
        CaseStudy evaluationCase = caseStudy(
                "case-2",
                "CASE-02",
                "provider-evaluation",
                CaseType.EVALUATION,
                List.of(otherCaseEvidence.getId())
        );
        TimelineEvent timeline = new TimelineEvent(
                "timeline-1",
                "2026.06–07",
                "Delivery loop",
                "Hard-coded paths",
                "Completed multi-target routing",
                "Created a deliverable version",
                List.of("project-1"),
                List.of(),
                List.of(),
                List.of("evidence-1")
        );
        return new PortfolioSnapshot(
                "1.0",
                "2026-07-14.1",
                OffsetDateTime.parse("2026-07-14T12:00:00+08:00"),
                owner,
                List.of(project),
                List.of(multilingualCase, evaluationCase),
                List.of(),
                List.of(),
                List.of(question, caseQuestion, otherCaseQuestion),
                List.of(
                        evidence,
                        caseEvidence,
                        pendingCaseEvidence,
                        rawCaseEvidence,
                        otherCaseEvidence
                ),
                List.of(timeline)
        );
    }

    private static CaseStudy caseStudy(
            String id,
            String code,
            String slug,
            CaseType type,
            List<String> evidenceIds
    ) {
        return new CaseStudy(
                id,
                code,
                slug,
                type,
                "Case title",
                "Case summary",
                "Case problem",
                List.of("Action"),
                List.of("Decision"),
                List.of("Verification"),
                "Case outcome",
                List.of("Limitation"),
                AchievementStatus.IMPLEMENTED_TESTED,
                ContributionType.PRIMARY,
                "project-1",
                List.of(),
                evidenceIds,
                List.of("timeline-1"),
                List.of()
        );
    }

    private static EvidenceRecord evidence(
            String id,
            EvidenceStatus publicStatus,
            boolean rawContentPublic
    ) {
        return new EvidenceRecord(
                id,
                "E-CASE",
                "Case evidence",
                EvidenceType.DOCUMENT,
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-14"),
                1,
                "Case evidence summary",
                publicStatus,
                rawContentPublic
        );
    }

    private static final class CountingRepository implements PublicPortfolioRepository {

        private final RuntimeContentSnapshot snapshot;
        private int reads;

        private CountingRepository(PortfolioSnapshot snapshot) {
            this.snapshot = new RuntimeContentSnapshot(
                    snapshot,
                    "sha256:test-runtime-bundle",
                    Instant.parse("2026-07-21T00:00:00Z")
            );
        }

        @Override
        public RuntimeContentSnapshot getSnapshot() {
            reads++;
            return snapshot;
        }
    }
}
