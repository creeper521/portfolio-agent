package com.portfolio.agent.portfolio.mapper;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.portfolio.domain.AchievementStatus;
import com.portfolio.agent.portfolio.domain.CaseStudy;
import com.portfolio.agent.portfolio.domain.CaseType;
import com.portfolio.agent.portfolio.domain.ClaimCategory;
import com.portfolio.agent.portfolio.domain.ContributionType;
import com.portfolio.agent.portfolio.domain.EvidenceRecord;
import com.portfolio.agent.portfolio.domain.EvidenceStatus;
import com.portfolio.agent.portfolio.domain.EvidenceType;
import com.portfolio.agent.portfolio.domain.OwnerProfile;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.ProjectStatus;
import com.portfolio.agent.portfolio.domain.QuestionDefinition;
import com.portfolio.agent.portfolio.domain.TimelineEvent;
import com.portfolio.agent.portfolio.dto.response.PublicContentResponse;
import com.portfolio.agent.portfolio.dto.response.QuestionPresetResponse;
import com.portfolio.agent.portfolio.service.result.CaseDetails;
import com.portfolio.agent.portfolio.service.result.ProjectDetails;
import com.portfolio.agent.portfolio.service.result.PublicContent;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioResponseMapperTest {

    private final PortfolioResponseMapper mapper = new PortfolioResponseMapper();

    @Test
    void mapsCasesAndZeroOneManyCaseRelationsWithoutChangingProjectRelations() {
        PublicContentResponse response =
                mapper.toPublicContentResponse(publicContentWithThreeCases());

        assertThat(response.getCases())
                .extracting("slug")
                .containsExactly(
                        "multilingual-image-preservation",
                        "test-role-reset",
                        "codegraph-evaluation"
                );
        assertThat(response.getCases())
                .filteredOn(item -> item.getSlug().equals("multilingual-image-preservation"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getProjectSlug()).isEqualTo("sql-audit");
                    assertThat(item.getEvidence()).extracting("id")
                            .containsExactly("evidence-case-multilingual");
                });
        assertThat(response.getCases())
                .filteredOn(item -> item.getSlug().equals("test-role-reset"))
                .singleElement()
                .satisfies(item -> assertThat(item.getProjectSlug()).isNull());

        assertThat(response.getCaseSlugsByEvidenceId())
                .containsEntry(
                        "evidence-case-role-reset-guide-and-acceptance",
                        List.of("test-role-reset")
                );

        assertThat(response.getQuestionPresets())
                .filteredOn(item -> item.getId().equals("question-project-overview"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getProjectSlug()).isEqualTo("sql-audit");
                    assertThat(item.getCaseSlugs()).isEmpty();
                });
        assertThat(response.getQuestionPresets())
                .filteredOn(item -> item.getId().equals("question-case-role-reset-overview"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getProjectSlug()).isNull();
                    assertThat(item.getCaseSlugs()).containsExactly("test-role-reset");
                });
        assertThat(response.getQuestionPresets())
                .filteredOn(item -> item.getId().equals("question-mixed-comparison"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getProjectSlug()).isEqualTo("sql-audit");
                    assertThat(item.getCaseSlugs()).containsExactly(
                            "multilingual-image-preservation",
                            "codegraph-evaluation"
                    );
                });
        assertThat(response.getQuestionPresets())
                .filteredOn(item -> item.getId().equals("question-multi-project"))
                .singleElement()
                .satisfies(item ->
                        assertThat(item.getProjectSlug()).isEqualTo("sql-audit"));

        assertThat(response.getTimeline())
                .filteredOn(item -> item.getId().equals("timeline-mixed"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getProjectSlugs()).containsExactly("sql-audit");
                    assertThat(item.getCaseSlugs()).containsExactly(
                            "test-role-reset",
                            "multilingual-image-preservation"
                    );
                });
    }

    @Test
    void publicContentCaseRelationsAreDeeplyImmutable() {
        PublicContentResponse response =
                mapper.toPublicContentResponse(publicContentWithThreeCases());

        assertThatThrownBy(() -> response.getCases().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> response.getCaseSlugsByEvidenceId()
                .put("other", List.of("case")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> response.getCaseSlugsByEvidenceId()
                .get("evidence-case-role-reset-guide-and-acceptance")
                .add("other-case"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void serializesExplicitNullProjectSlugForCaseOnlyQuestionPreset() throws Exception {
        PublicContentResponse response =
                mapper.toPublicContentResponse(publicContentWithThreeCases());
        QuestionPresetResponse caseOnly = response.getQuestionPresets().stream()
                .filter(item -> item.getId().equals("question-case-role-reset-overview"))
                .findFirst()
                .orElseThrow();
        ObjectMapper objectMapper = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(caseOnly));

        assertThat(json.has("projectSlug")).isTrue();
        assertThat(json.get("projectSlug").isNull()).isTrue();
    }

    @Test
    void rejectsUnknownIdAmongNonEmptyQuestionProjectIds() {
        PublicContent content = publicContentWithThreeCases();
        PublicContent invalid = withQuestions(
                content,
                List.of(question(
                        "question-unknown-project",
                        List.of("project-1", "missing-project"),
                        List.of()
                ))
        );

        assertThatThrownBy(() -> mapper.toPublicContentResponse(invalid))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing-project");
    }

    private static PublicContent publicContentWithThreeCases() {
        ProjectProfile project = project();
        EvidenceRecord projectEvidence = evidence(
                "evidence-project",
                "E-01",
                "Project evidence"
        );
        EvidenceRecord multilingualEvidence = evidence(
                "evidence-case-multilingual",
                "E-CASE-01",
                "Multilingual evidence"
        );
        EvidenceRecord roleResetEvidence = evidence(
                "evidence-case-role-reset-guide-and-acceptance",
                "E-CASE-02",
                "Role reset evidence"
        );
        EvidenceRecord evaluationEvidence = evidence(
                "evidence-case-codegraph-evaluation",
                "E-CASE-03",
                "CodeGraph evidence"
        );

        CaseDetails multilingual = caseDetails(
                caseStudy(
                        "case-multilingual",
                        "CASE-01",
                        "multilingual-image-preservation",
                        CaseType.FEATURE,
                        "project-1",
                        multilingualEvidence.getId()
                ),
                multilingualEvidence,
                "sql-audit"
        );
        CaseDetails roleReset = caseDetails(
                caseStudy(
                        "case-role-reset",
                        "CASE-02",
                        "test-role-reset",
                        CaseType.FEATURE,
                        null,
                        roleResetEvidence.getId()
                ),
                roleResetEvidence,
                null
        );
        CaseDetails evaluation = caseDetails(
                caseStudy(
                        "case-codegraph-evaluation",
                        "CASE-03",
                        "codegraph-evaluation",
                        CaseType.EVALUATION,
                        "project-1",
                        evaluationEvidence.getId()
                ),
                evaluationEvidence,
                "sql-audit"
        );

        List<QuestionDefinition> questions = List.of(
                question(
                        "question-project-overview",
                        List.of("project-1"),
                        List.of()
                ),
                question(
                        "question-case-role-reset-overview",
                        List.of(),
                        List.of("case-role-reset")
                ),
                question(
                        "question-mixed-comparison",
                        List.of("project-1"),
                        List.of("case-multilingual", "case-codegraph-evaluation")
                ),
                question(
                        "question-multi-project",
                        List.of("project-2", "project-1"),
                        List.of()
                )
        );

        TimelineEvent mixedTimeline = new TimelineEvent(
                "timeline-mixed",
                "2026.07",
                "Mixed relations",
                "Problem",
                "Action",
                "Impact",
                List.of("project-1"),
                List.of("case-role-reset", "case-multilingual"),
                List.of(),
                List.of(roleResetEvidence.getId(), multilingualEvidence.getId())
        );

        Map<String, List<String>> projectSlugsByEvidenceId = new LinkedHashMap<>();
        projectSlugsByEvidenceId.put(projectEvidence.getId(), List.of("sql-audit"));
        Map<String, List<String>> caseSlugsByEvidenceId = new LinkedHashMap<>();
        caseSlugsByEvidenceId.put(
                multilingualEvidence.getId(),
                List.of("multilingual-image-preservation")
        );
        caseSlugsByEvidenceId.put(roleResetEvidence.getId(), List.of("test-role-reset"));
        caseSlugsByEvidenceId.put(evaluationEvidence.getId(), List.of("codegraph-evaluation"));

        return new PublicContent(
                "2026-07-23.1",
                "sha256:test",
                OffsetDateTime.parse("2026-07-23T10:00:00+08:00"),
                new OwnerProfile(
                        "Owner",
                        "Java Developer",
                        "Summary",
                        "https://github.com/example",
                        "owner@example.com",
                        "/resume.pdf"
                ),
                List.of(
                        new ProjectDetails(
                                project,
                                List.of(projectEvidence),
                                List.of("What did you build?")
                        ),
                        new ProjectDetails(
                                secondProject(),
                                List.of(),
                                List.of()
                        )
                ),
                List.of(multilingual, roleReset, evaluation),
                List.of(),
                List.of(),
                List.of(
                        projectEvidence,
                        multilingualEvidence,
                        roleResetEvidence,
                        evaluationEvidence
                ),
                List.of(mixedTimeline),
                projectSlugsByEvidenceId,
                caseSlugsByEvidenceId,
                Map.of(),
                questions
        );
    }

    private static ProjectProfile project() {
        return new ProjectProfile(
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
                List.of("Verified"),
                "Outcome",
                "Handoff",
                ProjectStatus.DELIVERED,
                ContributionType.PRIMARY,
                List.of(),
                List.of("evidence-project"),
                List.of("timeline-mixed")
        );
    }

    private static ProjectProfile secondProject() {
        return new ProjectProfile(
                "project-2",
                "P-02",
                "secondary-project",
                "Secondary project",
                "Summary",
                "Background",
                List.of("Responsibility"),
                "Solution",
                List.of("Decision"),
                List.of("Java"),
                List.of("Verified"),
                "Outcome",
                "Handoff",
                ProjectStatus.DELIVERED,
                ContributionType.PRIMARY,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static CaseStudy caseStudy(
            String id,
            String code,
            String slug,
            CaseType type,
            String projectId,
            String evidenceId
    ) {
        return new CaseStudy(
                id,
                code,
                slug,
                type,
                "Case title " + code,
                "Case summary " + code,
                "Case problem " + code,
                List.of("Action"),
                List.of("Decision"),
                List.of("Verification"),
                "Outcome",
                List.of("Limitation"),
                AchievementStatus.IMPLEMENTED_TESTED,
                ContributionType.PRIMARY,
                projectId,
                List.of(),
                List.of(evidenceId),
                List.of("timeline-mixed"),
                List.of()
        );
    }

    private static CaseDetails caseDetails(
            CaseStudy caseStudy,
            EvidenceRecord evidence,
            String projectSlug
    ) {
        return new CaseDetails(
                caseStudy,
                List.of(evidence),
                List.of("Suggested question"),
                projectSlug
        );
    }

    private static EvidenceRecord evidence(String id, String code, String title) {
        return new EvidenceRecord(
                id,
                code,
                title,
                EvidenceType.DOCUMENT,
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-14"),
                1,
                "Evidence summary",
                EvidenceStatus.APPROVED,
                false
        );
    }

    private static QuestionDefinition question(
            String id,
            List<String> projectIds,
            List<String> caseIds
    ) {
        return new QuestionDefinition(
                id,
                "Question " + id,
                List.of(),
                List.of("INTERVIEWER"),
                projectIds,
                caseIds,
                List.of("OVERVIEW"),
                List.of(ClaimCategory.OUTCOME),
                List.of("HOME"),
                true,
                10
        );
    }

    private static PublicContent withQuestions(
            PublicContent content,
            List<QuestionDefinition> questions
    ) {
        return new PublicContent(
                content.getContentVersion(),
                content.getRuntimeBundleHash(),
                content.getPublishedAt(),
                content.getOwner(),
                content.getProjects(),
                content.getCases(),
                content.getClaims(),
                content.getClaimEvidenceLinks(),
                content.getEvidence(),
                content.getTimeline(),
                content.getProjectSlugsByEvidenceId(),
                content.getCaseSlugsByEvidenceId(),
                content.getClaimIdsByEvidenceId(),
                questions
        );
    }
}
