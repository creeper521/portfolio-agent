package com.portfolio.agent.portfolio.repository.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.portfolio.domain.AchievementStatus;
import com.portfolio.agent.portfolio.domain.CareerTrack;
import com.portfolio.agent.portfolio.domain.CaseCollection;
import com.portfolio.agent.portfolio.domain.CaseStudy;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.ProjectDisplayTier;
import com.portfolio.agent.portfolio.domain.ProjectNature;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PublicPortfolioSchemaFourContractTest {

    private static final Set<String> REMOVED_PROJECT_IDS = Set.of(
            "context-engineering-project",
            "technical-writing-project",
            "engineering-delivery-learning-project");

    @Test
    void publicPortfolioUsesProjectsForMainlinesAndCollectionsForIndependentCases()
            throws IOException {
        PortfolioSnapshot snapshot = readSnapshot();
        Map<String, ProjectProfile> projects = snapshot.getProjects().stream()
                .collect(Collectors.toMap(ProjectProfile::getId, Function.identity()));
        Map<String, CaseStudy> cases = snapshot.getCases().stream()
                .collect(Collectors.toMap(CaseStudy::getId, Function.identity()));

        assertThat(snapshot.getSchemaVersion()).isEqualTo("4.0");
        assertThat(snapshot.getContentVersion()).isEqualTo("2026-07-29.1");
        assertThat(snapshot.getProjects()).extracting(ProjectProfile::getId)
                .containsExactly(
                        "sql-audit-project",
                        "activity-engineering-project",
                        "role-reset-tool-project",
                        "personal-agent-platform-project",
                        "image-audit-project");
        assertThat(snapshot.getCollections()).extracting(CaseCollection::getSlug)
                .containsExactly(
                        "open-source-evaluation",
                        "engineering-operations",
                        "technical-writing");
        assertThat(snapshot.getCases()).hasSize(49);

        assertProject(projects.get("sql-audit-project"),
                CareerTrack.JAVA_BACKEND, ProjectNature.TOOL, ProjectDisplayTier.PRIMARY, 0);
        assertProject(projects.get("activity-engineering-project"),
                CareerTrack.JAVA_BACKEND, ProjectNature.WORKSTREAM, ProjectDisplayTier.PRIMARY, 24);
        assertProject(projects.get("role-reset-tool-project"),
                CareerTrack.JAVA_BACKEND, ProjectNature.TOOL, ProjectDisplayTier.PRIMARY, 1);
        assertProject(projects.get("personal-agent-platform-project"),
                CareerTrack.AGENT, ProjectNature.INTEGRATION_PROTOTYPE,
                ProjectDisplayTier.PRIMARY, 4);
        assertProject(projects.get("image-audit-project"),
                CareerTrack.JAVA_BACKEND, ProjectNature.TOOL, ProjectDisplayTier.SECONDARY, 2);

        assertThat(projects.get("activity-engineering-project").getFeaturedCaseIds())
                .containsExactly(
                        "case-public-k-10",
                        "case-public-a-01",
                        "case-public-a-05",
                        "case-public-a-06",
                        "case-public-a-14",
                        "case-public-a-15");
        assertThat(projects.get("role-reset-tool-project").getFeaturedCaseIds())
                .containsExactly("case-role-reset");
        assertThat(projects.get("personal-agent-platform-project").getFeaturedCaseIds())
                .containsExactly(
                        "case-public-t-08",
                        "case-public-t-09",
                        "case-public-t-10",
                        "case-public-t-11");
        assertThat(projects.get("image-audit-project").getFeaturedCaseIds())
                .containsExactly("case-multilingual-upload", "case-public-t-07");

        List<CaseStudy> independentCases = snapshot.getCases().stream()
                .filter(item -> item.getProjectId() == null)
                .toList();
        assertThat(independentCases).hasSize(18);
        assertThat(independentCases)
                .allSatisfy(item -> assertThat(item.getCollectionIds()).isNotEmpty());
        assertThat(independentCases).filteredOn(item ->
                        item.getCollectionIds().contains("open-source-evaluation"))
                .hasSize(6);
        assertThat(independentCases).filteredOn(item ->
                        item.getCollectionIds().contains("engineering-operations"))
                .hasSize(9);
        assertThat(independentCases).filteredOn(item ->
                        item.getCollectionIds().contains("technical-writing"))
                .hasSize(3);

        assertThat(cases.get("case-role-reset").getProjectId())
                .isEqualTo("role-reset-tool-project");
        assertThat(cases.get("case-multilingual-upload").getProjectId())
                .isEqualTo("image-audit-project");
        assertThat(cases.values()).filteredOn(item ->
                        "image-audit-project".equals(item.getProjectId()))
                .hasSize(2);

        assertThat(snapshot.getCases()).filteredOn(item ->
                        caseNumberBetween(item.getCode(), 22, 36))
                .allSatisfy(item -> assertThat(item.getAchievementStatus())
                        .isEqualTo(AchievementStatus.INVESTIGATED));
        assertThat(snapshot.getCases()).filteredOn(item -> "CASE-45".equals(item.getCode()))
                .singleElement()
                .satisfies(item -> assertThat(item.getAchievementStatus())
                        .isEqualTo(AchievementStatus.LEARNING));

        assertThat(snapshot.getClaims())
                .noneMatch(claim -> REMOVED_PROJECT_IDS.contains(claim.getSubjectId()));
        assertThat(snapshot.getTimeline())
                .allSatisfy(event -> assertThat(event.getProjectIds())
                        .doesNotContainAnyElementsOf(REMOVED_PROJECT_IDS));
        assertThat(snapshot.getQuestions())
                .allSatisfy(question -> assertThat(question.getProjectIds())
                        .doesNotContainAnyElementsOf(REMOVED_PROJECT_IDS));

        PortfolioSnapshot published = snapshot.withPublishedAt(
                OffsetDateTime.parse("2026-07-29T12:00:00+08:00"));
        assertThatCode(() -> new PortfolioSnapshotValidator().validate(published))
                .doesNotThrowAnyException();
    }

    private PortfolioSnapshot readSnapshot() throws IOException {
        ClassPathResource resource =
                new ClassPathResource("public-data/bundle/portfolio.json");
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        return new PortfolioSnapshotJsonReader(objectMapper)
                .readBundle(resource.getInputStream().readAllBytes());
    }

    private void assertProject(
            ProjectProfile project,
            CareerTrack careerTrack,
            ProjectNature projectNature,
            ProjectDisplayTier displayTier,
            int caseCount
    ) {
        assertThat(project).isNotNull();
        assertThat(project.getCareerTrack()).isEqualTo(careerTrack);
        assertThat(project.getProjectNature()).isEqualTo(projectNature);
        assertThat(project.getDisplayTier()).isEqualTo(displayTier);
        assertThat(projectCaseCount(project.getId())).isEqualTo(caseCount);
    }

    private long projectCaseCount(String projectId) {
        try {
            return readSnapshot().getCases().stream()
                    .filter(item -> projectId.equals(item.getProjectId()))
                    .count();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private boolean caseNumberBetween(String code, int first, int last) {
        if (code == null || !code.startsWith("CASE-")) {
            return false;
        }
        int number = Integer.parseInt(code.substring(5));
        return number >= first && number <= last;
    }
}
