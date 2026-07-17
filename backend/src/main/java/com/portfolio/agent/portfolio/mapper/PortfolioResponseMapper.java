package com.portfolio.agent.portfolio.mapper;

import com.portfolio.agent.portfolio.dto.response.EvidenceResponse;
import com.portfolio.agent.portfolio.dto.response.OwnerResponse;
import com.portfolio.agent.portfolio.dto.response.PortfolioHomeResponse;
import com.portfolio.agent.portfolio.dto.response.PublicContentResponse;
import com.portfolio.agent.portfolio.dto.response.ProjectDetailResponse;
import com.portfolio.agent.portfolio.dto.response.ProjectSummaryResponse;
import com.portfolio.agent.portfolio.dto.response.TimelineEventResponse;
import com.portfolio.agent.portfolio.service.result.PortfolioOverview;
import com.portfolio.agent.portfolio.service.result.PublicContent;
import com.portfolio.agent.portfolio.service.result.ProjectDetails;
import org.springframework.stereotype.Component;

import java.util.List;

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

    public PublicContentResponse toPublicContentResponse(PublicContent content) {
        return new PublicContentResponse(
                content.getContentVersion(),
                content.getPublishedAt(),
                OwnerResponse.from(content.getOwner()),
                content.getProjects().stream().map(this::toProjectResponse).toList(),
                content.getEvidence().stream()
                        .map(item -> EvidenceResponse.from(
                                item,
                                content.getProjectSlugsByEvidenceId()
                                        .getOrDefault(item.getId(), List.of())
                        ))
                        .toList(),
                content.getTimeline().stream().map(TimelineEventResponse::from).toList()
        );
    }
}
