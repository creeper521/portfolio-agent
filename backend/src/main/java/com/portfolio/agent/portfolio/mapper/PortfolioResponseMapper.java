package com.portfolio.agent.portfolio.mapper;

import com.portfolio.agent.portfolio.dto.response.EvidenceResponse;
import com.portfolio.agent.portfolio.dto.response.OwnerResponse;
import com.portfolio.agent.portfolio.dto.response.PortfolioHomeResponse;
import com.portfolio.agent.portfolio.dto.response.ProjectDetailResponse;
import com.portfolio.agent.portfolio.dto.response.ProjectSummaryResponse;
import com.portfolio.agent.portfolio.service.model.PortfolioOverview;
import com.portfolio.agent.portfolio.service.model.ProjectDetails;
import org.springframework.stereotype.Component;

@Component
public class PortfolioResponseMapper {

    public PortfolioHomeResponse toPortfolioResponse(PortfolioOverview overview) {
        return new PortfolioHomeResponse(
                overview.getContentVersion(),
                overview.getPublishedAt(),
                OwnerResponse.from(overview.getOwner()),
                overview.getProjects().stream()
                        .map(ProjectSummaryResponse::from)
                        .toList()
        );
    }

    public ProjectDetailResponse toProjectResponse(ProjectDetails details) {
        return ProjectDetailResponse.from(
                details.getProject(),
                details.getEvidence().stream().map(EvidenceResponse::from).toList(),
                details.getSuggestedQuestions()
        );
    }
}
