package com.portfolio.agent.portfolio.service;

import com.portfolio.agent.portfolio.domain.CaseStudy;
import com.portfolio.agent.portfolio.domain.ClaimEvidenceLink;
import com.portfolio.agent.portfolio.domain.EvidenceRecord;
import com.portfolio.agent.portfolio.domain.EvidenceStatus;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.QuestionDefinition;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.exception.CaseNotFoundException;
import com.portfolio.agent.portfolio.exception.ProjectNotFoundException;
import com.portfolio.agent.portfolio.repository.PublicPortfolioRepository;
import com.portfolio.agent.portfolio.service.result.CaseDetails;
import com.portfolio.agent.portfolio.service.result.PortfolioOverview;
import com.portfolio.agent.portfolio.service.result.PublicContent;
import com.portfolio.agent.portfolio.service.result.ProjectDetails;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PortfolioService {

    private final PublicPortfolioRepository repository;

    public PortfolioService(PublicPortfolioRepository repository) {
        this.repository = repository;
    }

    public PortfolioOverview getPortfolio() {
        RuntimeContentSnapshot snapshot = repository.getSnapshot();
        return new PortfolioOverview(
                snapshot.getContentVersion(),
                snapshot.getPublishedAt(),
                snapshot.getOwner(),
                snapshot.getProjects()
        );
    }

    public ProjectDetails getProject(String slug) {
        RuntimeContentSnapshot snapshot = repository.getSnapshot();
        ProjectProfile project = findProject(snapshot, slug);
        return toProjectDetails(snapshot, project);
    }

    public List<CaseDetails> getCases() {
        RuntimeContentSnapshot snapshot = repository.getSnapshot();
        return snapshot.getCases().stream()
                .map(caseStudy -> toCaseDetails(snapshot, caseStudy))
                .toList();
    }

    public CaseDetails getCase(String slug) {
        RuntimeContentSnapshot snapshot = repository.getSnapshot();
        CaseStudy caseStudy = findCase(snapshot, slug);
        return toCaseDetails(snapshot, caseStudy);
    }

    public PublicContent getPublicContent() {
        RuntimeContentSnapshot snapshot = repository.getSnapshot();
        List<ProjectDetails> projects = snapshot.getProjects().stream()
                .map(project -> toProjectDetails(snapshot, project))
                .toList();
        List<CaseDetails> cases = snapshot.getCases().stream()
                .map(caseStudy -> toCaseDetails(snapshot, caseStudy))
                .toList();
        List<EvidenceRecord> evidence = snapshot.getEvidence().stream()
                .filter(item -> item.getPublicStatus() == EvidenceStatus.APPROVED)
                .filter(item -> Boolean.FALSE.equals(item.getRawContentPublic()))
                .toList();
        Map<String, List<String>> projectSlugsByEvidenceId = new LinkedHashMap<>();
        Map<String, List<String>> caseSlugsByEvidenceId = new LinkedHashMap<>();
        Map<String, List<String>> claimIdsByEvidenceId = new LinkedHashMap<>();
        for (ProjectDetails projectDetails : projects) {
            String projectSlug = projectDetails.getProject().getSlug();
            for (EvidenceRecord evidenceRecord : projectDetails.getEvidence()) {
                projectSlugsByEvidenceId
                        .computeIfAbsent(evidenceRecord.getId(), ignored -> new ArrayList<>())
                        .add(projectSlug);
            }
        }
        for (CaseDetails caseDetails : cases) {
            String caseSlug = caseDetails.getCaseStudy().getSlug();
            for (EvidenceRecord evidenceRecord : caseDetails.getEvidence()) {
                caseSlugsByEvidenceId
                        .computeIfAbsent(evidenceRecord.getId(), ignored -> new ArrayList<>())
                        .add(caseSlug);
            }
        }
        for (ClaimEvidenceLink link : snapshot.getClaimEvidenceLinks()) {
            claimIdsByEvidenceId
                    .computeIfAbsent(link.getEvidenceId(), ignored -> new ArrayList<>())
                    .add(link.getClaimId());
        }
        return new PublicContent(
                snapshot.getContentVersion(),
                snapshot.getRuntimeBundleHash(),
                snapshot.getPublishedAt(),
                snapshot.getOwner(),
                projects,
                cases,
                snapshot.getClaims(),
                snapshot.getClaimEvidenceLinks(),
                evidence,
                snapshot.getTimeline(),
                projectSlugsByEvidenceId,
                caseSlugsByEvidenceId,
                claimIdsByEvidenceId,
                snapshot.getQuestionPresets()
        );
    }

    private ProjectDetails toProjectDetails(
            RuntimeContentSnapshot snapshot,
            ProjectProfile project
    ) {
        Set<String> evidenceIds = Set.copyOf(project.getEvidenceIds());

        List<EvidenceRecord> evidence = snapshot.getEvidence().stream()
                .filter(item -> evidenceIds.contains(item.getId()))
                .filter(item -> item.getPublicStatus() == EvidenceStatus.APPROVED)
                .filter(item -> Boolean.FALSE.equals(item.getRawContentPublic()))
                .toList();

        List<String> suggestedQuestions = snapshot.getQuestions().stream()
                .filter(question -> question.getProjectIds().contains(project.getId()))
                .map(QuestionDefinition::getText)
                .toList();

        return new ProjectDetails(project, evidence, suggestedQuestions);
    }

    private CaseDetails toCaseDetails(
            RuntimeContentSnapshot snapshot,
            CaseStudy caseStudy
    ) {
        Set<String> evidenceIds = Set.copyOf(caseStudy.getEvidenceIds());

        List<EvidenceRecord> evidence = snapshot.getEvidence().stream()
                .filter(item -> evidenceIds.contains(item.getId()))
                .filter(item -> item.getPublicStatus() == EvidenceStatus.APPROVED)
                .filter(item -> Boolean.FALSE.equals(item.getRawContentPublic()))
                .toList();

        List<String> suggestedQuestions = snapshot.getQuestions().stream()
                .filter(question -> question.getCaseIds().contains(caseStudy.getId()))
                .map(QuestionDefinition::getText)
                .toList();

        String projectSlug = caseStudy.getProjectId() == null
                ? null
                : snapshot.getProjects().stream()
                        .filter(project -> project.getId().equals(caseStudy.getProjectId()))
                        .findFirst()
                        .orElseThrow()
                        .getSlug();

        return new CaseDetails(caseStudy, evidence, suggestedQuestions, projectSlug);
    }

    private ProjectProfile findProject(RuntimeContentSnapshot snapshot, String slug) {
        return snapshot.getProjects().stream()
                .filter(project -> project.getSlug().equals(slug))
                .findFirst()
                .orElseThrow(() -> new ProjectNotFoundException(slug));
    }

    private CaseStudy findCase(RuntimeContentSnapshot snapshot, String slug) {
        return snapshot.getCases().stream()
                .filter(caseStudy -> caseStudy.getSlug().equals(slug))
                .findFirst()
                .orElseThrow(() -> new CaseNotFoundException(slug));
    }
}
