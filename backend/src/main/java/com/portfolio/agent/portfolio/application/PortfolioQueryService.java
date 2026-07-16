package com.portfolio.agent.portfolio.application;

import com.portfolio.agent.portfolio.api.dto.EvidenceResponse;
import com.portfolio.agent.portfolio.api.dto.OwnerResponse;
import com.portfolio.agent.portfolio.api.dto.PortfolioHomeResponse;
import com.portfolio.agent.portfolio.api.dto.ProjectDetailResponse;
import com.portfolio.agent.portfolio.api.dto.ProjectSummaryResponse;
import com.portfolio.agent.portfolio.application.exception.ProjectNotFoundException;
import com.portfolio.agent.portfolio.domain.model.EvidenceStatus;
import com.portfolio.agent.portfolio.domain.model.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.model.ProjectProfile;
import com.portfolio.agent.portfolio.domain.model.QuestionDefinition;
import com.portfolio.agent.portfolio.domain.repository.PublicPortfolioRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class PortfolioQueryService {

    private final PublicPortfolioRepository repository;

    public PortfolioQueryService(PublicPortfolioRepository repository) {
        this.repository = repository;
    }

    public PortfolioHomeResponse getPortfolio() {
        PortfolioSnapshot snapshot = repository.getSnapshot();
        return new PortfolioHomeResponse(
                snapshot.getContentVersion(),
                snapshot.getPublishedAt(),
                OwnerResponse.from(snapshot.getOwner()),
                snapshot.getProjects().stream().map(ProjectSummaryResponse::from).toList()
        );
    }

    public ProjectDetailResponse getProject(String slug) {
        PortfolioSnapshot snapshot = repository.getSnapshot();
        ProjectProfile project = findProject(slug);
        Set<String> evidenceIds = Set.copyOf(project.getEvidenceIds());

        List<EvidenceResponse> evidence = snapshot.getEvidence().stream()
                .filter(item -> evidenceIds.contains(item.getId()))
                .filter(item -> item.getPublicStatus() == EvidenceStatus.APPROVED)
                .filter(item -> !item.getRawContentPublic())
                .map(EvidenceResponse::from)
                .toList();

        List<String> suggestedQuestions = snapshot.getQuestions().stream()
                .filter(question -> project.getId().equals(question.getProjectId()))
                .filter(question -> project.getQuestionIds().contains(question.getId()))
                .map(QuestionDefinition::getSuggestion)
                .toList();

        return new ProjectDetailResponse(
                project.getSlug(),
                project.getTitle(),
                project.getSummary(),
                project.getBackground(),
                project.getResponsibilities(),
                project.getSolution(),
                project.getKeyDecisions(),
                project.getTechnologies(),
                project.getVerification(),
                project.getOutcome(),
                project.getHandoff(),
                project.getStatus(),
                project.getContributionType(),
                evidence,
                suggestedQuestions
        );
    }

    private ProjectProfile findProject(String slug) {
        return repository.getSnapshot().getProjects().stream()
                .filter(project -> project.getSlug().equals(slug))
                .findFirst()
                .orElseThrow(() -> new ProjectNotFoundException(slug));
    }
}
