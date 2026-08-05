package com.portfolio.agent.portfolio.mapper;

import com.portfolio.agent.portfolio.dto.response.CaseDetailResponse;
import com.portfolio.agent.portfolio.dto.response.CaseCollectionResponse;
import com.portfolio.agent.portfolio.dto.response.CaseSummaryResponse;
import com.portfolio.agent.portfolio.dto.response.ClaimEvidenceLinkResponse;
import com.portfolio.agent.portfolio.dto.response.ClaimResponse;
import com.portfolio.agent.portfolio.dto.response.EvidenceResponse;
import com.portfolio.agent.portfolio.dto.response.OwnerResponse;
import com.portfolio.agent.portfolio.dto.response.PortfolioHomeResponse;
import com.portfolio.agent.portfolio.dto.response.ProjectDetailResponse;
import com.portfolio.agent.portfolio.dto.response.ProjectSummaryResponse;
import com.portfolio.agent.portfolio.dto.response.PublicContentResponse;
import com.portfolio.agent.portfolio.dto.response.QuestionPresetResponse;
import com.portfolio.agent.portfolio.dto.response.TimelineEventResponse;
import com.portfolio.agent.portfolio.service.result.CaseDetails;
import com.portfolio.agent.portfolio.service.result.PortfolioOverview;
import com.portfolio.agent.portfolio.service.result.ProjectDetails;
import com.portfolio.agent.portfolio.service.result.PublicContent;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PortfolioResponseMapper {

    public PortfolioHomeResponse toPortfolioResponse(PortfolioOverview overview) {
        return new PortfolioHomeResponse(
                overview.getContentVersion(),
                overview.getPublishedAt(),
                OwnerResponse.from(overview.getOwner()),
                overview.getProjects().stream()
                        .map(project -> ProjectSummaryResponse.from(
                                project, overview.getCaseCount(project.getId())))
                        .toList(),
                overview.getCollections().stream()
                        .map(CaseCollectionResponse::from)
                        .toList()
        );
    }

    public ProjectDetailResponse toProjectResponse(ProjectDetails details) {
        return ProjectDetailResponse.from(
                details.getProject(),
                details.getEvidence().stream().map(EvidenceResponse::from).toList(),
                details.getSuggestedQuestions(),
                details.getCaseCount(),
                details.getFeaturedCases().stream()
                        .map(this::toCaseSummaryResponse)
                        .toList()
        );
    }

    public List<CaseSummaryResponse> toCaseResponses(List<CaseDetails> details) {
        return details.stream()
                .map(this::toCaseSummaryResponse)
                .toList();
    }

    public CaseDetailResponse toCaseResponse(CaseDetails details) {
        return CaseDetailResponse.from(
                details.getCaseStudy(),
                details.getProjectSlug(),
                details.getCollectionSlugs(),
                details.getEvidence().stream().map(EvidenceResponse::from).toList(),
                details.getSuggestedQuestions()
        );
    }

    public PublicContentResponse toPublicContentResponse(PublicContent content) {
        Map<String, String> projectSlugsById = projectSlugsById(content);
        Map<String, String> caseSlugsById = caseSlugsById(content);

        return new PublicContentResponse(
                content.getContentVersion(),
                content.getRuntimeBundleHash(),
                content.getPublishedAt(),
                OwnerResponse.from(content.getOwner()),
                content.getCollections().stream()
                        .map(CaseCollectionResponse::from)
                        .toList(),
                content.getProjects().stream().map(this::toProjectResponse).toList(),
                content.getCases().stream().map(this::toCaseResponse).toList(),
                content.getClaims().stream().map(ClaimResponse::new).toList(),
                content.getClaimEvidenceLinks().stream()
                        .map(ClaimEvidenceLinkResponse::new).toList(),
                content.getEvidence().stream()
                        .map(item -> EvidenceResponse.from(
                                item,
                                content.getProjectSlugsByEvidenceId()
                                        .getOrDefault(item.getId(), List.of()),
                                content.getClaimIdsByEvidenceId()
                                        .getOrDefault(item.getId(), List.of())
                        ))
                        .toList(),
                content.getTimeline().stream()
                        .map(event -> TimelineEventResponse.from(
                                event,
                                resolveSlugs(event.getProjectIds(), projectSlugsById),
                                resolveSlugs(event.getCaseIds(), caseSlugsById)
                        ))
                        .toList(),
                content.getCaseSlugsByEvidenceId(),
                content.getQuestionPresets().stream()
                        .filter(question -> question.getContractStatus()
                                == com.portfolio.agent.portfolio.domain.PresetContractStatus.ACTIVE)
                        .map(question -> QuestionPresetResponse.from(
                                question,
                                firstProjectSlug(
                                        question.getProjectIds(),
                                        content.getProjects(),
                                        projectSlugsById
                                ),
                                resolveSlugs(question.getCaseIds(), caseSlugsById)
                        ))
                        .toList()
        );
    }

    private CaseSummaryResponse toCaseSummaryResponse(CaseDetails details) {
        return CaseSummaryResponse.from(
                details.getCaseStudy(),
                details.getProjectSlug(),
                details.getCollectionSlugs()
        );
    }

    private Map<String, String> projectSlugsById(PublicContent content) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        content.getProjects().forEach(details ->
                result.put(details.getProject().getId(), details.getProject().getSlug()));
        return result;
    }

    private Map<String, String> caseSlugsById(PublicContent content) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        content.getCases().forEach(details ->
                result.put(details.getCaseStudy().getId(), details.getCaseStudy().getSlug()));
        return result;
    }

    private String firstProjectSlug(
            List<String> projectIds,
            List<ProjectDetails> projects,
            Map<String, String> projectSlugsById
    ) {
        if (projectIds.isEmpty()) {
            return null;
        }

        projectIds.forEach(id -> requiredSlug(id, projectSlugsById));

        return projects.stream()
                .map(ProjectDetails::getProject)
                .filter(project -> projectIds.contains(project.getId()))
                .map(project -> project.getSlug())
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException("Missing validated public project relation"));
    }

    private List<String> resolveSlugs(
            List<String> ids,
            Map<String, String> slugsById
    ) {
        return ids.stream()
                .map(id -> requiredSlug(id, slugsById))
                .toList();
    }

    private String requiredSlug(String id, Map<String, String> slugsById) {
        String slug = slugsById.get(id);
        if (slug == null) {
            throw new IllegalStateException("Missing validated public relation: " + id);
        }
        return slug;
    }
}
