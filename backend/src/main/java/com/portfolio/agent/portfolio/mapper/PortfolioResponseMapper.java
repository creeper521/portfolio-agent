package com.portfolio.agent.portfolio.mapper;

import com.portfolio.agent.portfolio.dto.response.CaseDetailResponse;
import com.portfolio.agent.portfolio.dto.response.AgentAvailabilityResponse;
import com.portfolio.agent.portfolio.dto.response.CaseCollectionResponse;
import com.portfolio.agent.portfolio.dto.response.CaseSummaryResponse;
import com.portfolio.agent.portfolio.dto.response.ClaimEvidenceLinkResponse;
import com.portfolio.agent.portfolio.dto.response.ClaimResponse;
import com.portfolio.agent.portfolio.dto.response.EvidenceResponse;
import com.portfolio.agent.portfolio.dto.response.OwnerResponse;
import com.portfolio.agent.portfolio.dto.response.PortfolioSnapshotResponse;
import com.portfolio.agent.portfolio.dto.response.ProjectDetailResponse;
import com.portfolio.agent.portfolio.dto.response.QuestionPresetResponse;
import com.portfolio.agent.portfolio.dto.response.TimelineEventResponse;
import com.portfolio.agent.portfolio.domain.PresetContractStatus;
import com.portfolio.agent.portfolio.service.result.CaseDetails;
import com.portfolio.agent.portfolio.service.result.ProjectDetails;
import com.portfolio.agent.portfolio.service.result.PublicContent;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 响应映射器：把公开内容领域对象转换为对外 HTTP 响应 DTO。
 *
 * <p>只做形状转换，不引入新内容：证据只携带公开元数据；问题预设只输出 ACTIVE 契约；
 * 时间线与问题中的 id 引用会被解析为可对外访问的 slug。id→slug 解析失败时抛出
 * {@link IllegalStateException}——引用完整性已由快照校验器保证，此处失败意味着内部
 * 数据损坏而非访客输入问题。
 */
@Component
public class PortfolioResponseMapper {

    /**
     * 把项目详情（含证据、建议问题、精选案例摘要）映射为项目详情响应。
     */
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

    /**
     * 把案例详情（含所属项目 slug、合集 slug、证据与建议问题）映射为案例详情响应。
     */
    public CaseDetailResponse toCaseResponse(CaseDetails details) {
        return CaseDetailResponse.from(
                details.getCaseStudy(),
                details.getProjectSlug(),
                details.getCollectionSlugs(),
                details.getEvidence().stream().map(EvidenceResponse::from).toList(),
                details.getSuggestedQuestions()
        );
    }

    /**
     * 映射完整快照响应，Agent 可用性按"可用"处理。
     */
    public PortfolioSnapshotResponse toPortfolioSnapshotResponse(PublicContent content) {
        return toPortfolioSnapshotResponse(content, AgentAvailabilityResponse.available());
    }

    /**
     * 映射完整快照响应并附带 Agent 可用性。
     *
     * <p>映射规则：项目/案例/断言/关联/证据逐组转换为对应响应对象；证据响应附加其
     * 回链的项目 slug 与 Claim id；时间线把 projectIds/caseIds 解析为 slug；问题预设
     * 仅保留 contractStatus=ACTIVE 的条目，并解析首个关联项目 slug 与案例 slug 列表。
     */
    public PortfolioSnapshotResponse toPortfolioSnapshotResponse(
            PublicContent content,
            AgentAvailabilityResponse agentAvailability) {
        Map<String, String> projectSlugsById = projectSlugsById(content);
        Map<String, String> caseSlugsById = caseSlugsById(content);

        return new PortfolioSnapshotResponse(
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
                                == PresetContractStatus.ACTIVE)
                        .map(question -> QuestionPresetResponse.from(
                                question,
                                firstProjectSlug(
                                        question.getProjectIds(),
                                        content.getProjects(),
                                        projectSlugsById
                                ),
                                resolveSlugs(question.getCaseIds(), caseSlugsById)
                        ))
                        .toList(),
                agentAvailability
        );
    }

    /** 把案例详情映射为列表/卡片用的摘要响应（不含证据与建议问题）。 */
    private CaseSummaryResponse toCaseSummaryResponse(CaseDetails details) {
        return CaseSummaryResponse.from(
                details.getCaseStudy(),
                details.getProjectSlug(),
                details.getCollectionSlugs()
        );
    }

    /** 构建项目 id 到 slug 的映射（保持快照顺序），供引用解析使用。 */
    private Map<String, String> projectSlugsById(PublicContent content) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        content.getProjects().forEach(details ->
                result.put(details.getProject().getId(), details.getProject().getSlug()));
        return result;
    }

    /** 构建案例 id 到 slug 的映射（保持快照顺序），供引用解析使用。 */
    private Map<String, String> caseSlugsById(PublicContent content) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        content.getCases().forEach(details ->
                result.put(details.getCaseStudy().getId(), details.getCaseStudy().getSlug()));
        return result;
    }

    /**
     * 解析问题预设展示关联中的首个项目 slug。
     *
     * <p>先确认每个关联 id 都能解析到 slug，再按快照中的项目顺序取第一个命中者；
     * 无关联时返回 null，id 完整却找不到对应项目则视为内部数据错误。
     */
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

    /** 把 id 列表逐个解析为 slug 列表（保持原顺序，缺失即抛异常）。 */
    private List<String> resolveSlugs(
            List<String> ids,
            Map<String, String> slugsById
    ) {
        return ids.stream()
                .map(id -> requiredSlug(id, slugsById))
                .toList();
    }

    /** 解析单个 id 的 slug；id 不在映射中说明已通过校验的数据被破坏，直接抛内部错误。 */
    private String requiredSlug(String id, Map<String, String> slugsById) {
        String slug = slugsById.get(id);
        if (slug == null) {
            throw new IllegalStateException("Missing validated public relation: " + id);
        }
        return slug;
    }
}
