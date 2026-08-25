package com.portfolio.agent.portfolio.service;

import com.portfolio.agent.portfolio.domain.CaseStudy;
import com.portfolio.agent.portfolio.domain.CaseCollection;
import com.portfolio.agent.portfolio.domain.ClaimEvidenceLink;
import com.portfolio.agent.portfolio.domain.EvidenceRecord;
import com.portfolio.agent.portfolio.domain.EvidenceStatus;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.QuestionDefinition;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.repository.PublicPortfolioRepository;
import com.portfolio.agent.portfolio.service.result.CaseDetails;
import com.portfolio.agent.portfolio.service.result.PublicContent;
import com.portfolio.agent.portfolio.service.result.ProjectDetails;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 作品集公开内容的聚合服务。
 *
 * <p>从 {@link PublicPortfolioRepository} 读取已审定的运行时快照，逐项目/案例组装证据、
 * 建议问题与精选案例，仅保留 publicStatus 为 APPROVED 且 rawContentPublic 为 false 的
 * Evidence（即不外泄原始内容），并构建证据到项目、案例、声明的反向索引，
 * 供 HTTP 层映射为对外只读响应。
 *
 * <p>关键不变量：任何路径下都不会返回非 APPROVED 证据或允许公开原始内容的证据；
 * 引用关系（精选案例、所属合集）在快照校验阶段已保证存在，缺失时直接抛出
 * {@link IllegalStateException}，说明快照本身不合法。
 */
@Service
public class PortfolioService {

    private final PublicPortfolioRepository repository;

    public PortfolioService(PublicPortfolioRepository repository) {
        this.repository = repository;
    }

    /**
     * 读取快照并聚合为对外公开内容模型。
     *
     * <p>数据来源为仓库当前生效的运行时快照；返回值包含内容版本、发布时间、所有者、
     * 项目/案例详情、声明与声明-证据关联、过滤后的证据、时间线、三个反向索引以及预设问题。
     *
     * @return 一次性组装完成的公开内容聚合对象，调用方可安全复用
     */
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
                snapshot.getCollections(),
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

    /**
     * 将单个项目档案组装为详情对象。
     *
     * <p>按项目的 evidenceIds 反查快照中的证据并应用 APPROVED/非原始内容双重过滤；
     * 关联问题取 projectIds 包含该项目的预设问题文本；案例数为该项目的全部案例，
     * 精选案例仅按 featuredCaseIds 顺序组装。
     *
     * @param snapshot 完整运行时快照，用于反查证据、问题与案例
     * @param project  待组装的项目档案
     * @return 含证据、建议问题、案例数量与精选案例的项目详情
     * @throws IllegalStateException featuredCaseIds 引用的案例在快照中不存在（快照校验缺口）
     */
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

        List<CaseStudy> projectCases = snapshot.getCases().stream()
                .filter(caseStudy -> project.getId().equals(caseStudy.getProjectId()))
                .toList();
        Map<String, CaseStudy> casesById = projectCases.stream()
                .collect(java.util.stream.Collectors.toMap(
                        CaseStudy::getId,
                        caseStudy -> caseStudy
                ));
        List<CaseDetails> featuredCases = project.getFeaturedCaseIds().stream()
                .map(caseId -> {
                    CaseStudy caseStudy = casesById.get(caseId);
                    if (caseStudy == null) {
                        throw new IllegalStateException(
                                "Missing validated featured case relation: " + caseId);
                    }
                    return toCaseDetails(snapshot, caseStudy);
                })
                .toList();

        return new ProjectDetails(
                project, evidence, suggestedQuestions, projectCases.size(), featuredCases);
    }

    /**
     * 将单个案例组装为详情对象。
     *
     * <p>按案例的 evidenceIds 反查证据并应用 APPROVED/非原始内容双重过滤；关联问题取
     * caseIds 包含该案例的预设问题文本；同时反查所属项目 slug 与所属合集 slug 列表。
     *
     * @param snapshot  完整运行时快照，用于反查证据、问题、项目与合集
     * @param caseStudy 待组装的案例
     * @return 含证据、建议问题、所属项目 slug 与合集 slug 的案例详情
     * @throws IllegalStateException collectionIds 引用的合集在快照中不存在（快照校验缺口）
     */
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

        Map<String, CaseCollection> collectionsById = snapshot.getCollections().stream()
                .collect(java.util.stream.Collectors.toMap(
                        CaseCollection::getId,
                        collection -> collection
                ));
        List<String> collectionSlugs = caseStudy.getCollectionIds().stream()
                .map(collectionId -> {
                    CaseCollection collection = collectionsById.get(collectionId);
                    if (collection == null) {
                        throw new IllegalStateException(
                                "Missing validated collection relation: " + collectionId);
                    }
                    return collection.getSlug();
                })
                .toList();

        return new CaseDetails(
                caseStudy, evidence, suggestedQuestions, projectSlug, collectionSlugs);
    }

}
