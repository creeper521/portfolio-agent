package com.portfolio.agent.turn.capability.portfolio.knowledge;

import com.portfolio.agent.portfolio.domain.EvidenceRecord;
import com.portfolio.agent.portfolio.domain.EvidenceStatus;
import com.portfolio.agent.portfolio.domain.Claim;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;
import com.portfolio.agent.portfolio.domain.RuntimeContentSnapshot;
import com.portfolio.agent.portfolio.domain.RuntimeRetrievalContent;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.CaseStudy;
import com.portfolio.agent.portfolio.domain.QuestionDefinition;
import com.portfolio.agent.portfolio.domain.PresetContractStatus;
import com.portfolio.agent.portfolio.repository.PublicPortfolioRepository;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 知识网关的本地快照适配器：把审定公开快照投影为回答知识 RuntimeAnswerContent。
 *
 * <p>从 {@link PublicPortfolioRepository} 读取运行时内容快照，逐项目/逐案例装配
 * {@link AnswerKnowledge}，并转换公开时间线与本地检索语料。隐私边界：Evidence
 * 只保留 publicStatus=APPROVED 且 rawContentPublic=false 的元数据投影（不携带
 * 原始内容）；claim 只关联 DIRECT 且审核通过的 Evidence 链接；时间线事件必须
 * 全部引用快照内存在且归属一致的实体，否则整条丢弃（fail-closed）。
 */
public class LocalPortfolioKnowledgeAdapter implements PortfolioKnowledgeGateway {

    private final PublicPortfolioRepository repository;

    public LocalPortfolioKnowledgeAdapter(PublicPortfolioRepository repository) {
        this.repository = repository;
    }

    /**
     * 读取当前快照并装配完整的回答知识内容。
     *
     * @return 与快照同 contentVersion、同 runtimeBundleHash 的 RuntimeAnswerContent
     */
    @Override
    public RuntimeAnswerContent getContent() {
        RuntimeContentSnapshot snapshot = repository.getSnapshot();
        List<AnswerKnowledge> projects = snapshot.getProjects().stream()
                .map(project -> toKnowledge(snapshot, project))
                .toList();
        List<AnswerKnowledge> cases = snapshot.getCases().stream()
                .map(caseStudy -> toCaseKnowledge(snapshot, caseStudy))
                .toList();
        return new RuntimeAnswerContent(
                snapshot.getContentVersion(),
                snapshot.getRuntimeBundleHash(),
                projects,
                cases,
                snapshot.getRetrievalContent().map(this::toRetrievalCorpus).orElse(null),
                toTimeline(snapshot)
        );
    }

    /**
     * 把单个案例装配为 AnswerKnowledge。
     *
     * <p>只保留归属该案例的激活预设问题、APPROVED 且非原始公开的 Evidence 元数据，
     * 以及案例 claim 投影；每个 claim 的直接证据取自 DIRECT+APPROVED 链接。
     * 字段映射上：problem 作背景、summary 复用作方案概述、limitations 拼接进 handoff，
     * 案例不带职业轨道（careerTrack 为 null）。
     */
    private AnswerKnowledge toCaseKnowledge(
            RuntimeContentSnapshot snapshot,
            CaseStudy value
    ) {
        Set<String> evidenceIds = Set.copyOf(value.getEvidenceIds());
        List<AnswerQuestion> questions = snapshot.getQuestions().stream()
                .filter(candidate -> belongsToExecutionSubject(candidate, value.getId()))
                .map(this::toQuestion)
                .toList();
        List<AnswerEvidence> evidence = snapshot.getEvidence().stream()
                .filter(candidate -> evidenceIds.contains(candidate.getId()))
                .filter(candidate -> candidate.getPublicStatus() == EvidenceStatus.APPROVED)
                .filter(candidate -> Boolean.FALSE.equals(candidate.getRawContentPublic()))
                .map(this::toEvidence)
                .toList();
        Set<String> approvedEvidenceIds = evidence.stream()
                .map(AnswerEvidence::getId)
                .collect(Collectors.toUnmodifiableSet());
        Map<String, List<String>> directEvidenceByClaimId = snapshot.getClaimEvidenceLinks().stream()
                .filter(link -> link.getSupportType()
                        == com.portfolio.agent.portfolio.domain.SupportType.DIRECT)
                .filter(link -> link.getReviewStatus()
                        == com.portfolio.agent.portfolio.domain.ReviewStatus.APPROVED)
                .filter(link -> approvedEvidenceIds.contains(link.getEvidenceId()))
                .collect(Collectors.groupingBy(
                        com.portfolio.agent.portfolio.domain.ClaimEvidenceLink::getClaimId,
                        Collectors.mapping(
                                com.portfolio.agent.portfolio.domain.ClaimEvidenceLink::getEvidenceId,
                                Collectors.toUnmodifiableList())));
        Map<String, Claim> claimsById = snapshot.getClaims().stream()
                .filter(claim -> claim.getSubjectType() == ClaimSubjectType.CASE)
                .filter(claim -> value.getId().equals(claim.getSubjectId()))
                .collect(Collectors.toUnmodifiableMap(Claim::getId, claim -> claim));
        List<AnswerClaimProjection> claims = value.getClaimIds().stream()
                .map(claimsById::get)
                .filter(Objects::nonNull)
                .map(claim -> new AnswerClaimProjection(
                        claim.getId(),
                        AnswerClaimCategory.valueOf(claim.getCategory().name()),
                        claim.getStatement(),
                        claim.getDetail(),
                        AnswerAchievementStatus.valueOf(claim.getAchievementStatus().name()),
                        AnswerContributionType.valueOf(claim.getContributionType().name()),
                        AnswerVerificationBasis.valueOf(claim.getVerificationBasis().name()),
                        AnswerClaimVerificationStatus.valueOf(claim.getVerificationStatus().name()),
                        AnswerMateriality.valueOf(claim.getMateriality().name()),
                        claim.getTopics(),
                        directEvidenceByClaimId.getOrDefault(claim.getId(), List.of())))
                .toList();
        return new AnswerKnowledge(
                AnswerSubjectType.CASE,
                value.getId(),
                value.getSlug(),
                value.getTitle(),
                value.getSummary(),
                value.getProblem(),
                value.getActions(),
                value.getSummary(),
                value.getDecisions(),
                value.getVerification(),
                value.getOutcome(),
                String.join(" ", value.getLimitations()),
                value.getAchievementStatus().name(),
                null,
                capabilityCodes(claims),
                questions,
                evidence,
                claims);
    }

    /**
     * 把快照时间线投影为公开时间线事件，逐条做引用完整性校验，不满足即整条丢弃。
     *
     * <p>校验包括：事件只能关联项目或案例其中一类；引用的项目/案例必须都存在；
     * 引用的 claim 必须归属事件列出的主体；引用的 Evidence 必须全部为
     * APPROVED 且 rawContentPublic=false，并且至少被事件引用的一个主体实际持有。
     */
    private List<AnswerTimelineEvent> toTimeline(RuntimeContentSnapshot snapshot) {
        Map<String, ProjectProfile> projectsById = snapshot.getProjects().stream()
                .collect(Collectors.toUnmodifiableMap(
                        ProjectProfile::getId,
                        project -> project));
        Map<String, CaseStudy> casesById = snapshot.getCases().stream()
                .collect(Collectors.toUnmodifiableMap(
                        CaseStudy::getId,
                        caseStudy -> caseStudy));
        Map<String, Claim> claimsById = snapshot.getClaims().stream()
                .collect(Collectors.toUnmodifiableMap(Claim::getId, claim -> claim));
        Set<String> approvedEvidenceIds = snapshot.getEvidence().stream()
                .filter(evidence -> evidence.getPublicStatus() == EvidenceStatus.APPROVED)
                .filter(evidence -> Boolean.FALSE.equals(evidence.getRawContentPublic()))
                .map(EvidenceRecord::getId)
                .collect(Collectors.toUnmodifiableSet());
        return snapshot.getTimeline().stream()
                // 事件必须二选一：要么只关联项目、要么只关联案例，不允许混合或两者皆空
                .filter(event -> event.getProjectIds().isEmpty()
                        != event.getCaseIds().isEmpty())
                .filter(event -> projectsById.keySet().containsAll(event.getProjectIds()))
                .filter(event -> casesById.keySet().containsAll(event.getCaseIds()))
                .filter(event -> event.getClaimIds().stream().allMatch(claimId -> {
                    Claim claim = claimsById.get(claimId);
                    return claim != null && (claim.getSubjectType() == ClaimSubjectType.PROJECT
                            ? event.getProjectIds().contains(claim.getSubjectId())
                            : event.getCaseIds().contains(claim.getSubjectId()));
                }))
                .filter(event -> approvedEvidenceIds.containsAll(event.getEvidenceIds()))
                .filter(event -> event.getEvidenceIds().stream().allMatch(evidenceId ->
                        event.getProjectIds().stream()
                                .map(projectsById::get)
                                .anyMatch(project -> project.getEvidenceIds().contains(evidenceId))
                        || event.getCaseIds().stream()
                                .map(casesById::get)
                                .anyMatch(caseStudy ->
                                        caseStudy.getEvidenceIds().contains(evidenceId))))
                .map(event -> new AnswerTimelineEvent(
                        event.getId(),
                        event.getDateLabel(),
                        event.getTitle(),
                        event.getProblem(),
                        event.getAction(),
                        event.getImpact(),
                        event.getProjectIds().stream()
                                .map(projectsById::get)
                                .map(ProjectProfile::getSlug)
                                .toList(),
                        event.getCaseIds().stream()
                                .map(casesById::get)
                                .map(CaseStudy::getSlug)
                                .toList(),
                        event.getClaimIds(),
                        event.getEvidenceIds()))
                .toList();
    }

    /** 把快照的本地检索内容（关键词索引、向量、分块）转换为不可变的回答检索语料。 */
    private AnswerRetrievalCorpus toRetrievalCorpus(RuntimeRetrievalContent source) {
        List<AnswerKeywordIndex.DocumentEntry> keywordDocuments = source.getKeywordIndex()
                .getDocuments().stream()
                .map(item -> new AnswerKeywordIndex.DocumentEntry(
                        item.getChunkId(), item.getDocumentLength(), item.getTermFrequencies()))
                .toList();
        AnswerKeywordIndex keywordIndex = new AnswerKeywordIndex(
                source.getKeywordIndex().getDocumentCount(),
                source.getKeywordIndex().getAverageDocumentLength(),
                keywordDocuments,
                source.getKeywordIndex().getDocumentFrequencies());
        Map<String, AnswerRetrievalChunk> chunks = source.getDocuments().stream()
                .collect(Collectors.toUnmodifiableMap(
                        com.portfolio.agent.portfolio.domain.RagDocument::getChunkId,
                        item -> new AnswerRetrievalChunk(
                                item.getChunkId(), item.getProjectSlugs(), item.getCaseSlugs(),
                                item.getClaimIds(),
                                item.getTopics(), item.getText(), item.getText().length())));
        return new AnswerRetrievalCorpus(
                keywordIndex, source.getVectorIndex().getVectors(), chunks,
                source.getManifest().getEmbeddingModelId(),
                source.getManifest().getEmbeddingArtifactSha256(),
                source.getManifest().getDimension());
    }

    /**
     * 把单个项目装配为 AnswerKnowledge。
     *
     * <p>只保留归属该项目的激活预设问题、APPROVED 且非原始公开的 Evidence 元数据，
     * 以及项目 claim 投影；每个 claim 的直接证据取自 DIRECT+APPROVED 链接，
     * 能力编码由已验证 claim 的 topics 汇总得出。
     */
    private AnswerKnowledge toKnowledge(RuntimeContentSnapshot snapshot, ProjectProfile value) {
        Set<String> evidenceIds = Set.copyOf(value.getEvidenceIds());

        List<AnswerQuestion> questions = snapshot.getQuestions().stream()
                .filter(candidate -> belongsToExecutionSubject(candidate, value.getId()))
                .map(this::toQuestion)
                .toList();

        List<AnswerEvidence> evidence = snapshot.getEvidence().stream()
                .filter(candidate -> evidenceIds.contains(candidate.getId()))
                .filter(candidate -> candidate.getPublicStatus() == EvidenceStatus.APPROVED)
                .filter(candidate -> Boolean.FALSE.equals(candidate.getRawContentPublic()))
                .map(this::toEvidence)
                .toList();
        Set<String> approvedEvidenceIds = evidence.stream()
                .map(AnswerEvidence::getId)
                .collect(Collectors.toUnmodifiableSet());
        Map<String, List<String>> directEvidenceByClaimId = snapshot.getClaimEvidenceLinks().stream()
                .filter(link -> link.getSupportType()
                        == com.portfolio.agent.portfolio.domain.SupportType.DIRECT)
                .filter(link -> link.getReviewStatus()
                        == com.portfolio.agent.portfolio.domain.ReviewStatus.APPROVED)
                .filter(link -> approvedEvidenceIds.contains(link.getEvidenceId()))
                .collect(Collectors.groupingBy(
                        com.portfolio.agent.portfolio.domain.ClaimEvidenceLink::getClaimId,
                        Collectors.mapping(
                                com.portfolio.agent.portfolio.domain.ClaimEvidenceLink::getEvidenceId,
                                Collectors.toUnmodifiableList())));
        Map<String, Claim> projectClaimsById = snapshot.getClaims().stream()
                .filter(claim -> claim.getSubjectType() == ClaimSubjectType.PROJECT)
                .filter(claim -> value.getId().equals(claim.getSubjectId()))
                .collect(Collectors.toUnmodifiableMap(Claim::getId, claim -> claim));
        List<AnswerClaimProjection> claims = value.getClaimIds().stream()
                .map(projectClaimsById::get)
                .filter(Objects::nonNull)
                .map(claim -> new AnswerClaimProjection(
                        claim.getId(),
                        AnswerClaimCategory.valueOf(claim.getCategory().name()),
                        claim.getStatement(),
                        claim.getDetail(),
                        AnswerAchievementStatus.valueOf(claim.getAchievementStatus().name()),
                        AnswerContributionType.valueOf(claim.getContributionType().name()),
                        AnswerVerificationBasis.valueOf(claim.getVerificationBasis().name()),
                        AnswerClaimVerificationStatus.valueOf(claim.getVerificationStatus().name()),
                        AnswerMateriality.valueOf(claim.getMateriality().name()),
                        claim.getTopics(),
                        directEvidenceByClaimId.getOrDefault(claim.getId(), List.of())))
                .toList();

        return new AnswerKnowledge(
                AnswerSubjectType.PROJECT,
                value.getId(),
                value.getSlug(),
                value.getTitle(),
                value.getSummary(),
                value.getBackground(),
                value.getResponsibilities(),
                value.getSolution(),
                value.getKeyDecisions(),
                value.getVerification(),
                value.getOutcome(),
                value.getHandoff(),
                value.getStatus().name(),
                value.getCareerTrack().name(),
                capabilityCodes(claims),
                questions,
                evidence,
                claims
        );
    }

    /** 汇总主体能力编码：仅取 VERIFIED claim 的 topics，归一化为大写并去重。 */
    private Set<String> capabilityCodes(List<AnswerClaimProjection> claims) {
        Set<String> capabilityCodes = new LinkedHashSet<>();
        claims.stream()
                .filter(claim -> claim.getVerificationStatus()
                        == AnswerClaimVerificationStatus.VERIFIED)
                .flatMap(claim -> claim.getTopics().stream())
                .map(this::normalizeCapabilityCode)
                .filter(code -> !code.isEmpty())
                .forEach(capabilityCodes::add);
        return Set.copyOf(capabilityCodes);
    }

    private String normalizeCapabilityCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    /** 判断预设问题是否属于该主体：仅当契约激活且契约主体 ID 匹配。 */
    private boolean belongsToExecutionSubject(
            QuestionDefinition question,
            String subjectId
    ) {
        return question.isActiveContract()
                && subjectId.equals(question.getContractSubjectId());
    }

    /** 把预设问题定义转换为回答层问题；契约未激活时 contractVersion 置空。 */
    private AnswerQuestion toQuestion(QuestionDefinition question) {
        return new AnswerQuestion(
                question.getId(),
                question.getText(),
                question.getAliases(),
                question.getText(),
                question.getPreferredClaimCategories().stream()
                        .map(category -> AnswerClaimCategory.valueOf(
                                category.name()))
                        .toList(),
                question.getContractStatus() == PresetContractStatus.ACTIVE
                        ? question.getContractVersion()
                        : null,
                question.getRequiredClaimIds(),
                question.getSupportingClaimIds(),
                question.getEvidenceRequirement()
                        .getMinimumApprovedEvidencePerRequiredClaim(),
                question.getContractStatus() == PresetContractStatus.ACTIVE,
                question.getContractSubjectId()
        );
    }

    /** 把 Evidence 记录投影为公开元数据；rawContentPublic 固定为 false，不暴露原始内容。 */
    private AnswerEvidence toEvidence(EvidenceRecord evidence) {
        return new AnswerEvidence(
                evidence.getId(),
                evidence.getCode(),
                evidence.getTitle(),
                evidence.getType().name(),
                evidence.getPeriodStart(),
                evidence.getPeriodEnd(),
                evidence.getSourceCount(),
                evidence.getSummary(),
                evidence.getPublicStatus().name(),
                false
        );
    }
}
