package com.portfolio.agent.evaluation.coverage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.evaluation.domain.AnswerResolution;
import com.portfolio.agent.evaluation.domain.ConversationAnswerScope;
import com.portfolio.agent.evaluation.domain.EvalCase;
import com.portfolio.agent.evaluation.domain.EvalGraderRule;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalMessage;
import com.portfolio.agent.evaluation.domain.EvalOrigin;
import com.portfolio.agent.evaluation.domain.EvalRiskLevel;
import com.portfolio.agent.evaluation.domain.EvalSeverity;
import com.portfolio.agent.evaluation.domain.EvalSplit;
import com.portfolio.agent.evaluation.domain.EvalSubjectRef;
import com.portfolio.agent.portfolio.domain.CaseStudy;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.ProjectDisplayTier;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.repository.file.PublicBundleLoader;
import com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EvalCoverageLinterTest {

    @Test
    void generatedSmokeDoesNotSatisfyDeepCoverageForChangedSubject() throws Exception {
        RuntimeContentSnapshot snapshot = loadRuntimeBundle();
        List<EvalCase> smokeCases = new EvalSmokeCaseGenerator().generate(snapshot);

        EvalCoverageReport report = new EvalCoverageLinter(Set.of("CASE:test-role-reset"))
                .lint(snapshot, smokeCases);

        assertThat(report.isValid()).isFalse();
        assertThat(report.getIssues()).extracting(EvalCoverageIssue::getCode)
                .contains("MISSING_DEEP_COVERAGE");
        assertThat(report.getIssues()).extracting(EvalCoverageIssue::getSubjectRef)
                .contains("CASE:test-role-reset");
        assertThat(report.getDeepCoveredSubjects()).doesNotContain("CASE:test-role-reset");
    }

    @Test
    void acceptsAnAuthoredHighRiskIntelligenceCaseForARequiredSubject() throws Exception {
        RuntimeContentSnapshot snapshot = loadRuntimeBundle();
        String subject = "CASE:test-role-reset";
        List<EvalCase> cases = new ArrayList<EvalCase>(
                new EvalSmokeCaseGenerator().generate(snapshot));
        cases.add(authoredDeepCase(subject));

        EvalCoverageReport report = new EvalCoverageLinter(Set.of(subject)).lint(snapshot, cases);

        assertThat(report.getDeepCoveredSubjects()).contains(subject);
        assertThat(report.getIssues()).extracting(EvalCoverageIssue::getSubjectRef)
                .doesNotContain(subject);
    }

    @Test
    void requiresMultiClaimOrPresetCasesButNotSecondaryProjectsForThatRule() throws Exception {
        RuntimeContentSnapshot snapshot = loadRuntimeBundle();
        ProjectProfile secondaryProject = findProject(snapshot, "image-upload-audit");
        CaseStudy multiClaimCase = findCase(snapshot, "test-role-reset");
        RuntimeContentSnapshot changed = snapshotWith(
                snapshot,
                replaceProject(snapshot, copyProject(
                        secondaryProject, ProjectDisplayTier.SECONDARY, List.of("claim-one", "claim-two"))),
                replaceCase(snapshot, copyCase(multiClaimCase, List.of("claim-one", "claim-two"),
                        multiClaimCase.getQuestionPresetIds())));

        EvalCoverageReport report = new EvalCoverageLinter(Set.of()).lint(changed, List.of());

        assertThat(report.getRequiredDeepSubjects())
                .contains("CASE:" + multiClaimCase.getSlug())
                .doesNotContain("PROJECT:" + secondaryProject.getSlug());
    }

    private EvalCase authoredDeepCase(String canonicalSubject) {
        EvalSubjectRef subject = new EvalSubjectRef(
                ClaimSubjectType.CASE, canonicalSubject.substring("CASE:".length()));
        return new EvalCase(
                "authored.deep.test-role-reset", "Authored deep coverage", EvalSplit.REGRESSION,
                EvalOrigin.HUMAN_AUTHORED, EvalRiskLevel.HIGH, "APPROVED", "reviewer",
                "REGRESSION", "Protect a changed public subject", "2026-08-04.1",
                List.of("deep"), new EvalCase.Input(List.of(new EvalMessage("user", "Role reset"))),
                new EvalCase.Oracle(List.of(subject)),
                new EvalCase.Expectations(List.of(AnswerResolution.ANSWERED),
                        List.of(ConversationAnswerScope.PORTFOLIO),
                        List.of(), List.of(), List.of(), List.of()),
                new EvalCase.Execution(List.of(EvalLayer.HTTP_E2E), 1),
                List.of(new EvalGraderRule("SUBJECT_MATCH", EvalSeverity.BLOCKING)),
                new EvalCase.Maintenance(List.of(subject), false));
    }

    private ProjectProfile findProject(RuntimeContentSnapshot snapshot, String slug) {
        for (ProjectProfile project : snapshot.getProjects()) {
            if (slug.equals(project.getSlug())) {
                return project;
            }
        }
        throw new IllegalArgumentException("Missing project: " + slug);
    }

    private CaseStudy findCase(RuntimeContentSnapshot snapshot, String slug) {
        for (CaseStudy caseStudy : snapshot.getCases()) {
            if (slug.equals(caseStudy.getSlug())) {
                return caseStudy;
            }
        }
        throw new IllegalArgumentException("Missing case: " + slug);
    }

    private List<ProjectProfile> replaceProject(RuntimeContentSnapshot snapshot,
                                                ProjectProfile replacement) {
        List<ProjectProfile> projects = new ArrayList<ProjectProfile>(snapshot.getProjects());
        for (int index = 0; index < projects.size(); index++) {
            if (replacement.getId().equals(projects.get(index).getId())) {
                projects.set(index, replacement);
                return projects;
            }
        }
        throw new IllegalArgumentException("Missing project replacement: " + replacement.getId());
    }

    private List<CaseStudy> replaceCase(RuntimeContentSnapshot snapshot, CaseStudy replacement) {
        List<CaseStudy> cases = new ArrayList<CaseStudy>(snapshot.getCases());
        for (int index = 0; index < cases.size(); index++) {
            if (replacement.getId().equals(cases.get(index).getId())) {
                cases.set(index, replacement);
                return cases;
            }
        }
        throw new IllegalArgumentException("Missing case replacement: " + replacement.getId());
    }

    private ProjectProfile copyProject(ProjectProfile source, ProjectDisplayTier displayTier,
                                       List<String> claimIds) {
        return new ProjectProfile(source.getId(), source.getCode(), source.getSlug(), source.getTitle(),
                source.getSummary(), source.getBackground(), source.getResponsibilities(),
                source.getSolution(), source.getKeyDecisions(), source.getTechnologies(),
                source.getVerification(), source.getOutcome(), source.getHandoff(), source.getStatus(),
                source.getContributionType(), source.getCareerTrack(), source.getProjectNature(),
                displayTier, source.getFeaturedCaseIds(), claimIds, source.getEvidenceIds(),
                source.getTimelineEventIds());
    }

    private CaseStudy copyCase(CaseStudy source, List<String> claimIds,
                               List<String> questionPresetIds) {
        return new CaseStudy(source.getId(), source.getCode(), source.getSlug(), source.getType(),
                source.getTitle(), source.getSummary(), source.getProblem(), source.getActions(),
                source.getDecisions(), source.getVerification(), source.getOutcome(),
                source.getLimitations(), source.getAchievementStatus(), source.getContributionType(),
                source.getProjectId(), source.getCollectionIds(), claimIds, source.getEvidenceIds(),
                source.getTimelineEventIds(), questionPresetIds);
    }

    private RuntimeContentSnapshot snapshotWith(RuntimeContentSnapshot source,
                                                List<ProjectProfile> projects, List<CaseStudy> cases) {
        PortfolioSnapshot content = new PortfolioSnapshot(source.getSchemaVersion(),
                source.getContentVersion(), source.getPublishedAt(), source.getOwner(), projects, cases,
                source.getCollections(), source.getClaims(), source.getClaimEvidenceLinks(),
                source.getQuestionPresets(), source.getApprovedEvidence(), source.getTimeline());
        return new RuntimeContentSnapshot(content, source.getRuntimeBundleHash(), source.getLoadedAt(),
                source.getRetrievalContent().orElse(null));
    }

    private RuntimeContentSnapshot loadRuntimeBundle() throws Exception {
        Set<String> names = Set.of(
                "manifest.json", "portfolio.json", "presentation.json", "rag-documents.jsonl",
                "keyword-index.json", "vector-index.bin", "checksums.json");
        Map<String, byte[]> files = new HashMap<String, byte[]>();
        for (String name : names) {
            files.put(name, resource("/public-data/bundle/" + name));
        }
        return new PublicBundleLoader(
                new ObjectMapper().findAndRegisterModules(),
                new PortfolioSnapshotValidator(), Clock.systemUTC()).load(files);
    }

    private byte[] resource(String path) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing test resource: " + path);
            }
            return input.readAllBytes();
        }
    }
}
