package com.portfolio.agent.portfolio.validation;

import com.portfolio.agent.portfolio.domain.AchievementStatus;
import com.portfolio.agent.portfolio.domain.CareerTrack;
import com.portfolio.agent.portfolio.domain.CaseCollection;
import com.portfolio.agent.portfolio.domain.CaseStudy;
import com.portfolio.agent.portfolio.domain.Claim;
import com.portfolio.agent.portfolio.domain.ClaimEvidenceLink;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;
import com.portfolio.agent.portfolio.domain.ClaimVerificationStatus;
import com.portfolio.agent.portfolio.domain.EvidenceRecord;
import com.portfolio.agent.portfolio.domain.EvidenceStatus;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.ProjectNature;
import com.portfolio.agent.portfolio.domain.QuestionDefinition;
import com.portfolio.agent.portfolio.domain.PresetContractStatus;
import com.portfolio.agent.common.text.StableQuestionNormalizer;
import com.portfolio.agent.portfolio.domain.ReviewStatus;
import com.portfolio.agent.portfolio.domain.TimelineEvent;
import com.portfolio.agent.portfolio.domain.SupportType;
import com.portfolio.agent.portfolio.domain.VerificationBasis;
import com.portfolio.agent.portfolio.exception.InvalidPortfolioSnapshotException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 公开快照校验器：发布契约的唯一运行时把关点。
 *
 * <p>快照在进入运行时（文件包加载或数据库导入）前必须整体通过本校验：结构完整性、
 * 各类对象内的字段约束、跨对象引用的存在性与归属、slug/code/id 的唯一性与命名空间
 * 互斥，以及断言口径（achievementStatus、verificationBasis 与 verificationStatus 的
 * 一致性）和 ACTIVE 预设问题契约。任何一条不满足都会抛出
 * {@link InvalidPortfolioSnapshotException}，阻止不合规内容对外发布。
 */
@Component
public class PortfolioSnapshotValidator {

    /** 允许加载的快照 schema 版本；新版本需同步扩展此集合与对应校验分支。 */
    private static final Set<String> SUPPORTED_SCHEMA_VERSIONS = Set.of("2.0", "3.0", "4.0");

    /** 对外 slug 的格式约束：仅小写字母、数字与连字符，长度 1 到 64。 */
    private static final Pattern SLUG_PATTERN = Pattern.compile("[a-z0-9-]{1,64}");

    /**
     * 校验整个快照；全部规则通过后正常返回，任一规则失败即抛出异常。
     *
     * <p>校验顺序：基础元数据与 owner → 八类集合非空与 id 唯一 → 项目/案例/合集三者的
     * id、code、slug 命名空间互斥 → 各对象字段约束与引用存在性 → 断言口径一致性 →
     * ACTIVE 预设契约 → 时间线引用与证据公开性。
     *
     * @param snapshot 待校验的公开快照
     * @throws InvalidPortfolioSnapshotException 任一校验规则不满足
     */
    public void validate(PortfolioSnapshot snapshot) {
        require(snapshot != null, "snapshot is required");
        require(snapshot.getSchemaVersion() != null
                        && SUPPORTED_SCHEMA_VERSIONS.contains(snapshot.getSchemaVersion()),
                "unsupported schemaVersion: " + snapshot.getSchemaVersion());
        require(hasText(snapshot.getContentVersion()), "contentVersion is required");
        require(snapshot.getPublishedAt() != null, "publishedAt is required");
        require(snapshot.getOwner() != null, "owner is required");
        require(hasText(snapshot.getOwner().getRole()), "owner role is required");
        require(hasText(snapshot.getOwner().getSummary()), "owner summary is required");

        List<ProjectProfile> projects = requiredList(snapshot.getProjects(), "projects");
        List<CaseStudy> cases = requiredList(snapshot.getCases(), "cases");
        List<CaseCollection> collections = requiredList(
                snapshot.getCollections(), "collections");
        List<Claim> claims = requiredList(snapshot.getClaims(), "claims");
        List<ClaimEvidenceLink> links = requiredList(
                snapshot.getClaimEvidenceLinks(), "claimEvidenceLinks");
        List<QuestionDefinition> questions = requiredList(snapshot.getQuestions(), "questions");
        List<EvidenceRecord> evidence = requiredList(snapshot.getEvidence(), "evidence");
        List<TimelineEvent> timeline = requiredList(snapshot.getTimeline(), "timeline");

        Map<String, ProjectProfile> projectsById = uniqueById(projects, ProjectProfile::getId, "project");
        Map<String, ProjectProfile> projectsBySlug = uniqueById(projects, ProjectProfile::getSlug,
                "project slug");
        Map<String, ProjectProfile> projectsByCode =
                uniqueById(projects, ProjectProfile::getCode, "project code");
        Map<String, CaseStudy> casesById = uniqueById(cases, CaseStudy::getId, "case");
        Map<String, CaseStudy> casesBySlug =
                uniqueById(cases, CaseStudy::getSlug, "case slug");
        Map<String, CaseStudy> casesByCode =
                uniqueById(cases, CaseStudy::getCode, "case code");
        Map<String, CaseCollection> collectionsById = uniqueById(
                collections, CaseCollection::getId, "collection");
        Map<String, CaseCollection> collectionsBySlug = uniqueById(
                collections, CaseCollection::getSlug, "collection slug");
        requireDisjoint(projectsById.keySet(), casesById.keySet(),
                "project and case ids must be disjoint");
        requireDisjoint(projectsById.keySet(), collectionsById.keySet(),
                "project and collection ids must be disjoint");
        requireDisjoint(casesById.keySet(), collectionsById.keySet(),
                "case and collection ids must be disjoint");
        requireDisjoint(projectsByCode.keySet(), casesByCode.keySet(),
                "project and case codes must be disjoint");
        requireDisjoint(projectsBySlug.keySet(), casesBySlug.keySet(),
                "project and case slugs must be disjoint");
        requireDisjoint(projectsBySlug.keySet(), collectionsBySlug.keySet(),
                "project and collection slugs must be disjoint");
        requireDisjoint(casesBySlug.keySet(), collectionsBySlug.keySet(),
                "case and collection slugs must be disjoint");
        Map<String, Claim> claimsById = uniqueById(claims, Claim::getId, "claim");
        Map<String, QuestionDefinition> questionsById = uniqueById(questions,
                QuestionDefinition::getId, "question");
        Map<String, EvidenceRecord> evidenceById = uniqueById(evidence, EvidenceRecord::getId,
                "evidence");
        uniqueById(links, ClaimEvidenceLink::getId, "claim evidence link");
        Map<String, TimelineEvent> timelineById = uniqueById(
                timeline, TimelineEvent::getId, "timeline");

        require(!projectsBySlug.isEmpty(), "at least one project is required");

        // 合集字段约束：slug 格式、标题与摘要非空、展示顺序非负。
        for (CaseCollection collection : collections) {
            require(hasText(collection.getSlug()), "collection slug is required");
            require(SLUG_PATTERN.matcher(collection.getSlug()).matches(),
                    "collection slug format is invalid: " + collection.getSlug());
            require(hasText(collection.getTitle()),
                    "collection title is required: " + collection.getId());
            require(hasText(collection.getSummary()),
                    "collection summary is required: " + collection.getId());
            require(collection.getDisplayOrder() >= 0,
                    "collection displayOrder must not be negative: " + collection.getId());
        }

        Map<String, List<ClaimEvidenceLink>> linksByClaimId = links.stream()
                .collect(java.util.stream.Collectors.groupingBy(ClaimEvidenceLink::getClaimId));
        // 关联约束：两端引用必须存在、支撑类型/范围必填、且只有 APPROVED 关联可进入公开快照。
        for (ClaimEvidenceLink link : links) {
            require(claimsById.containsKey(link.getClaimId()),
                    "claim evidence link reference does not exist: " + link.getClaimId());
            require(evidenceById.containsKey(link.getEvidenceId()),
                    "claim evidence link reference does not exist: " + link.getEvidenceId());
            require(link.getSupportType() != null,
                    "claim evidence link supportType is required: " + link.getId());
            require(hasText(link.getScope()),
                    "claim evidence link scope is required: " + link.getId());
            require(link.getReviewStatus() == ReviewStatus.APPROVED,
                    "claim evidence link must be APPROVED: " + link.getId());
        }

        // 断言约束：主体类型/分类/口径字段必填，主体引用必须存在，口径须与证据支撑一致。
        for (Claim claim : claims) {
            require(claim.getSubjectType() != null, "claim subjectType is required: " + claim.getId());
            require(hasText(claim.getSubjectId()), "claim subjectId is required: " + claim.getId());
            if (claim.getSubjectType() == ClaimSubjectType.PROJECT) {
                require(projectsById.containsKey(claim.getSubjectId()),
                        "claim subject reference does not exist: " + claim.getSubjectId());
            } else if (claim.getSubjectType() == ClaimSubjectType.CASE) {
                require(casesById.containsKey(claim.getSubjectId()),
                        "claim subject reference does not exist: " + claim.getSubjectId());
            }
            require(claim.getCategory() != null, "claim category is required: " + claim.getId());
            require(hasText(claim.getStatement()), "claim statement is required: " + claim.getId());
            require(claim.getAchievementStatus() != null,
                    "claim achievementStatus is required: " + claim.getId());
            require(claim.getVerificationBasis() != null,
                    "claim verificationBasis is required: " + claim.getId());
            require(claim.getVerificationStatus() != null,
                    "claim verificationStatus is required: " + claim.getId());
            require(claim.getMateriality() != null, "claim materiality is required: " + claim.getId());
            requiredNonBlankList(claim.getTopics(), "claim topics");
            validateVerification(claim, linksByClaimId.getOrDefault(claim.getId(), List.of()));
        }

        // 预设问题约束：必须关联项目或案例、文本与各标签列表非空、必须为确定性入口，
        // 且必答/支撑 Claim 引用各自无重复并互不重叠。
        for (QuestionDefinition question : questions) {
            requireAssociation(question.getProjectIds(), question.getCaseIds(), "question");
            for (String projectId : question.getProjectIds()) {
                require(projectsById.containsKey(projectId),
                        "question project reference does not exist: " + projectId);
            }
            for (String caseId : question.getCaseIds()) {
                require(casesById.containsKey(caseId),
                        "question case reference does not exist: " + caseId);
            }
            require(hasText(question.getText()), "question text is required");
            requiredNonBlankList(question.getAliases(), "question aliases");
            requiredNonBlankList(question.getAudiences(), "question audiences");
            requiredNonBlankList(question.getTopics(), "question topics");
            require(!question.getPreferredClaimCategories().isEmpty(),
                    "question preferredClaimCategories must not be empty");
            requiredNonBlankList(question.getPlacements(), "question placements");
            require(question.isDeterministicEntry(),
                    "published question must be a deterministic entry");
            validateUniqueNonBlankValues(question.getRequiredClaimIds(),
                    "question requiredClaimIds");
            validateUniqueNonBlankValues(question.getSupportingClaimIds(),
                    "question supportingClaimIds");
            require(question.getRequiredClaimIds().stream()
                            .noneMatch(question.getSupportingClaimIds()::contains),
                    "question requiredClaimIds and supportingClaimIds must be disjoint: "
                            + question.getId());
        }

        // 证据约束：公开快照内的证据必须全部 APPROVED、rawContentPublic 必须为 false
        // （只公开摘要不公开原始内容），时间段合法且来源数为正。
        for (EvidenceRecord item : evidence) {
            require(hasText(item.getCode()), "evidence code is required: " + item.getId());
            require(hasText(item.getTitle()), "evidence title is required: " + item.getId());
            require(item.getType() != null, "evidence type is required: " + item.getId());
            require(item.getPeriodStart() != null && item.getPeriodEnd() != null,
                    "evidence period is required: " + item.getId());
            require(!item.getPeriodEnd().isBefore(item.getPeriodStart()),
                    "evidence period is invalid: " + item.getId());
            require(item.getPublicStatus() == EvidenceStatus.APPROVED,
                    "evidence must be APPROVED: " + item.getId());
            require(item.getRawContentPublic() != null,
                    "evidence rawContentPublic is required: " + item.getId());
            require(!item.getRawContentPublic(),
                    "evidence raw content must not be public: " + item.getId());
            require(item.getSourceCount() > 0,
                    "evidence sourceCount must be positive: " + item.getId());
            require(hasText(item.getSummary()), "evidence summary is required: " + item.getId());
        }

        validateActiveQuestionContracts(questions, claimsById, linksByClaimId, evidenceById,
                projectsById, casesById);

        // 项目约束：文本字段与分类必填（schema 4.0 起三分类不得缺省/未分类），
        // 精选案例最多 6 个且必须属于本项目，claim/evidence/timeline 引用必须存在且归属正确。
        for (ProjectProfile project : projects) {
            require(hasText(project.getCode()), "project code is required: " + project.getId());
            require(hasText(project.getSlug()), "project slug is required");
            require(SLUG_PATTERN.matcher(project.getSlug()).matches(),
                    "project slug format is invalid: " + project.getSlug());
            require(hasText(project.getTitle()), "project title is required: " + project.getId());
            require(hasText(project.getSummary()), "project summary is required: " + project.getId());
            require(hasText(project.getBackground()),
                    "project background is required: " + project.getId());
            requiredNonBlankList(project.getResponsibilities(), "project responsibilities");
            require(hasText(project.getSolution()), "project solution is required: " + project.getId());
            requiredNonBlankList(project.getKeyDecisions(), "project keyDecisions");
            requiredNonBlankList(project.getTechnologies(), "project technologies");
            requiredNonBlankList(project.getVerification(), "project verification");
            require(hasText(project.getOutcome()), "project outcome is required: " + project.getId());
            require(hasText(project.getHandoff()), "project handoff is required: " + project.getId());
            require(project.getStatus() != null, "project status is required: " + project.getId());
            require(project.getContributionType() != null,
                    "project contributionType is required: " + project.getId());
            if ("4.0".equals(snapshot.getSchemaVersion())) {
                require(project.getCareerTrack() != null
                                && project.getCareerTrack() != CareerTrack.UNCLASSIFIED,
                        "project careerTrack must be classified: " + project.getId());
                require(project.getProjectNature() != null
                                && project.getProjectNature() != ProjectNature.UNCLASSIFIED,
                        "project projectNature must be classified: " + project.getId());
                require(project.getDisplayTier() != null,
                        "project displayTier is required: " + project.getId());
            }

            List<String> featuredCaseIds = requiredList(
                    project.getFeaturedCaseIds(), "project featuredCaseIds");
            validateNonBlankValues(featuredCaseIds, "project featuredCaseIds");
            require(featuredCaseIds.size() <= 6,
                    "project featuredCaseIds must contain at most 6 items: " + project.getId());
            Set<String> uniqueFeaturedCaseIds = new HashSet<>();
            for (String caseId : featuredCaseIds) {
                require(uniqueFeaturedCaseIds.add(caseId),
                        "duplicate featured case reference: " + caseId);
                CaseStudy featuredCase = casesById.get(caseId);
                require(featuredCase != null,
                        "featured case reference does not exist: " + caseId);
                require(project.getId().equals(featuredCase.getProjectId()),
                        "featured case must belong to project: " + caseId);
            }

            for (String claimId : requiredNonBlankList(project.getClaimIds(), "project claimIds")) {
                Claim claim = claimsById.get(claimId);
                require(claim != null, "project claim reference does not exist: " + claimId);
                require(project.getId().equals(claim.getSubjectId()),
                        "claim reference belongs to a different project: " + claimId);
            }

            for (String evidenceId : requiredNonBlankList(
                    project.getEvidenceIds(), "project evidenceIds")) {
                require(evidenceById.containsKey(evidenceId),
                        "project evidence reference does not exist: " + evidenceId);
            }
            for (String timelineEventId : requiredNonBlankList(
                    project.getTimelineEventIds(), "project timelineEventIds")) {
                require(timelineById.containsKey(timelineEventId),
                        "project timeline reference does not exist: " + timelineEventId);
            }
        }

        // 案例约束：叙事字段必填、所属项目与合集引用合法，claim 引用必须以本案例为主体，
        // evidence/timeline/questionPreset 引用必须存在。
        for (CaseStudy caseStudy : cases) {
            require(hasText(caseStudy.getCode()),
                    "case code is required: " + caseStudy.getId());
            require(hasText(caseStudy.getSlug()), "case slug is required");
            require(SLUG_PATTERN.matcher(caseStudy.getSlug()).matches(),
                    "case slug format is invalid: " + caseStudy.getSlug());
            require(caseStudy.getType() != null,
                    "case type is required: " + caseStudy.getId());
            require(hasText(caseStudy.getTitle()),
                    "case title is required: " + caseStudy.getId());
            require(hasText(caseStudy.getSummary()),
                    "case summary is required: " + caseStudy.getId());
            require(hasText(caseStudy.getProblem()),
                    "case problem is required: " + caseStudy.getId());
            requiredNonBlankList(caseStudy.getActions(), "case actions");
            validateNonBlankValues(requiredList(caseStudy.getDecisions(), "case decisions"),
                    "case decisions");
            requiredNonBlankList(caseStudy.getVerification(), "case verification");
            require(hasText(caseStudy.getOutcome()),
                    "case outcome is required: " + caseStudy.getId());
            requiredNonBlankList(caseStudy.getLimitations(), "case limitations");
            require(caseStudy.getAchievementStatus() != null,
                    "case achievementStatus is required: " + caseStudy.getId());
            require(caseStudy.getContributionType() != null,
                    "case contributionType is required: " + caseStudy.getId());

            if (caseStudy.getProjectId() != null) {
                require(hasText(caseStudy.getProjectId()),
                        "case projectId must not be blank: " + caseStudy.getId());
                require(projectsById.containsKey(caseStudy.getProjectId()),
                        "case project reference does not exist: " + caseStudy.getProjectId());
            }

            Set<String> uniqueCollectionIds = new HashSet<>();
            for (String collectionId : requiredList(
                    caseStudy.getCollectionIds(), "case collectionIds")) {
                require(hasText(collectionId),
                        "case collectionIds must not contain blank values");
                require(uniqueCollectionIds.add(collectionId),
                        "duplicate case collection reference: " + collectionId);
                require(collectionsById.containsKey(collectionId),
                        "case collection reference does not exist: " + collectionId);
            }

            for (String claimId : requiredNonBlankList(
                    caseStudy.getClaimIds(), "case claimIds")) {
                Claim claim = claimsById.get(claimId);
                require(claim != null, "case claim reference does not exist: " + claimId);
                require(claim.getSubjectType() == ClaimSubjectType.CASE
                                && caseStudy.getId().equals(claim.getSubjectId()),
                        "claim reference belongs to a different case: " + claimId);
            }
            for (String evidenceId : requiredNonBlankList(
                    caseStudy.getEvidenceIds(), "case evidenceIds")) {
                require(evidenceById.containsKey(evidenceId),
                        "case evidence reference does not exist: " + evidenceId);
            }
            for (String timelineEventId : requiredNonBlankList(
                    caseStudy.getTimelineEventIds(), "case timelineEventIds")) {
                require(timelineById.containsKey(timelineEventId),
                        "case timeline reference does not exist: " + timelineEventId);
            }
            for (String questionPresetId : requiredNonBlankList(
                    caseStudy.getQuestionPresetIds(), "case questionPresetIds")) {
                require(questionsById.containsKey(questionPresetId),
                        "case question reference does not exist: " + questionPresetId);
            }
        }

        uniqueById(evidence, EvidenceRecord::getCode, "evidence code");
        // 时间线约束：五段叙事文本必填，必须关联至少一个项目或案例，
        // 被引用的证据必须存在且为 APPROVED。
        for (TimelineEvent event : timeline) {
            require(hasText(event.getDateLabel()),
                    "timeline dateLabel is required: " + event.getId());
            require(hasText(event.getTitle()), "timeline title is required: " + event.getId());
            require(hasText(event.getProblem()), "timeline problem is required: " + event.getId());
            require(hasText(event.getAction()), "timeline action is required: " + event.getId());
            require(hasText(event.getImpact()), "timeline impact is required: " + event.getId());
            requireAssociation(event.getProjectIds(), event.getCaseIds(), "timeline");
            for (String projectId : event.getProjectIds()) {
                require(projectsById.containsKey(projectId),
                        "timeline project reference does not exist: " + projectId);
            }
            for (String caseId : event.getCaseIds()) {
                require(casesById.containsKey(caseId),
                        "timeline case reference does not exist: " + caseId);
            }
            for (String claimId : requiredNonBlankList(
                    event.getClaimIds(), "timeline claimIds")) {
                require(claimsById.containsKey(claimId),
                        "timeline claim reference does not exist: " + claimId);
            }
            for (String evidenceId : requiredNonBlankList(
                    event.getEvidenceIds(), "timeline evidenceIds")) {
                EvidenceRecord referenced = evidenceById.get(evidenceId);
                require(referenced != null,
                        "timeline evidence reference does not exist: " + evidenceId);
                require(referenced.getPublicStatus() == EvidenceStatus.APPROVED,
                        "timeline evidence must be APPROVED: " + evidenceId);
            }
        }
    }

    /**
     * 校验断言口径一致性：成果类断言必须有 DIRECT 证据；verificationStatus 不得超过
     * verificationBasis 允许的上限。
     *
     * <p>规则：achievementStatus 属于成果（见 {@link #isAchievement}）时，该断言必须至少
     * 有一条 DIRECT 关联；basis=EVIDENCE_SUPPORTED 时要求有 DIRECT 关联且状态为 VERIFIED；
     * basis=SELF_DECLARED/INFERRED 时状态不得为 VERIFIED；basis=UNSUPPORTED 时状态
     * 必须为 UNVERIFIED。
     *
     * @param claim 待校验断言
     * @param links 该断言的全部证据关联（已按 claimId 分组）
     */
    private static void validateVerification(Claim claim, List<ClaimEvidenceLink> links) {
        boolean hasDirect = links.stream().anyMatch(link -> link.getSupportType() == SupportType.DIRECT);
        if (isAchievement(claim.getAchievementStatus())) {
            require(hasDirect, "achievement claim requires an APPROVED DIRECT link: " + claim.getId());
        }
        if (claim.getVerificationBasis() == VerificationBasis.EVIDENCE_SUPPORTED) {
            require(hasDirect && claim.getVerificationStatus() == ClaimVerificationStatus.VERIFIED,
                    "claim verificationStatus does not match DIRECT evidence: " + claim.getId());
        } else if (claim.getVerificationBasis() == VerificationBasis.SELF_DECLARED
                || claim.getVerificationBasis() == VerificationBasis.INFERRED) {
            require(claim.getVerificationStatus() != ClaimVerificationStatus.VERIFIED,
                    "claim verificationStatus exceeds verificationBasis: " + claim.getId());
        } else if (claim.getVerificationBasis() == VerificationBasis.UNSUPPORTED) {
            require(claim.getVerificationStatus() == ClaimVerificationStatus.UNVERIFIED,
                    "claim verificationStatus exceeds unsupported basis: " + claim.getId());
        }
    }

    /**
     * 校验预设问题契约：非 ACTIVE 预设不得声明 Claim 契约；ACTIVE 预设必须满足完整的
     * 契约形态且文本/别名身份唯一。
     *
     * <p>ACTIVE 预设的规则：必须是确定性入口；contractSubjectId 非空、必须是已存在的项目
     * 或案例、且必须包含在自身的展示关联（projectIds/caseIds）中；requiredClaimIds 非空；
     * evidenceRequirement 必须为 publicOnly。每条 required/supporting Claim 再交给
     * {@link #validateActiveQuestionClaim} 校验。最后要求所有 ACTIVE 预设的标准问法与别名
     * 经归一化后互不冲突，避免匹配歧义。
     */
    private static void validateActiveQuestionContracts(
            List<QuestionDefinition> questions,
            Map<String, Claim> claimsById,
            Map<String, List<ClaimEvidenceLink>> linksByClaimId,
            Map<String, EvidenceRecord> evidenceById,
            Map<String, ProjectProfile> projectsById,
            Map<String, CaseStudy> casesById
    ) {
        Set<String> identities = new HashSet<>();
        for (QuestionDefinition question : questions) {
            if (question.getContractStatus() != PresetContractStatus.ACTIVE) {
                require(question.getRequiredClaimIds().isEmpty(),
                        "non-active question must not declare requiredClaimIds: "
                                + question.getId());
                require(question.getSupportingClaimIds().isEmpty(),
                        "non-active question must not declare supportingClaimIds: "
                                + question.getId());
                continue;
            }
            require(question.isDeterministicEntry(),
                    "active question must be a deterministic entry: " + question.getId());
            require(hasText(question.getContractSubjectId()),
                    "active question contractSubjectId is required: " + question.getId());
            require(projectsById.containsKey(question.getContractSubjectId())
                            || casesById.containsKey(question.getContractSubjectId()),
                    "active question contractSubjectId is unknown: " + question.getId());
            require(question.getProjectIds().contains(question.getContractSubjectId())
                            || question.getCaseIds().contains(question.getContractSubjectId()),
                    "active question contractSubjectId must be a display association: "
                            + question.getId());
            require(!question.getRequiredClaimIds().isEmpty(),
                    "active question requiredClaimIds must not be empty: " + question.getId());
            require(question.getEvidenceRequirement().isPublicOnly(),
                    "active question evidenceRequirement must be publicOnly: " + question.getId());

            String subjectId = question.getContractSubjectId();
            for (String claimId : question.getRequiredClaimIds()) {
                validateActiveQuestionClaim(question, subjectId, claimId, claimsById,
                        linksByClaimId, evidenceById, true);
            }
            for (String claimId : question.getSupportingClaimIds()) {
                validateActiveQuestionClaim(question, subjectId, claimId, claimsById,
                        linksByClaimId, evidenceById, false);
            }
            requireUniqueQuestionIdentity(identities, question.getText(), question.getId());
            for (String alias : question.getAliases()) {
                requireUniqueQuestionIdentity(identities, alias, question.getId());
            }
        }
    }

    /**
     * 校验 ACTIVE 预设引用的单条 Claim：必须存在、必须为 VERIFIED、主体必须与预设的
     * contractSubjectId 一致。
     *
     * <p>对 required Claim 额外校验证据规模：统计该 Claim 名下支撑方式为 DIRECT、
     * 关联状态为 APPROVED、且证据本身 APPROVED 并 rawContentPublic=false 的关联数，
     * 必须达到预设声明的 minimumApprovedEvidencePerRequiredClaim；supporting Claim
     * 不做此要求。
     *
     * @param required true 表示 requiredClaimIds 中的必答 Claim，false 表示支撑 Claim
     */
    private static void validateActiveQuestionClaim(
            QuestionDefinition question,
            String subjectId,
            String claimId,
            Map<String, Claim> claimsById,
            Map<String, List<ClaimEvidenceLink>> linksByClaimId,
            Map<String, EvidenceRecord> evidenceById,
            boolean required
    ) {
        Claim claim = claimsById.get(claimId);
        String prefix = required ? "required" : "supporting";
        require(claim != null, prefix + " claim does not exist: " + claimId);
        require(claim.getVerificationStatus() == ClaimVerificationStatus.VERIFIED,
                prefix + " claim must be VERIFIED: " + claimId);
        require(subjectId.equals(claim.getSubjectId()),
                prefix + " claim subject must match active question: " + claimId);
        if (!required) {
            return;
        }
        long approvedDirectEvidence = linksByClaimId.getOrDefault(claimId, List.of()).stream()
                .filter(link -> link.getSupportType() == SupportType.DIRECT)
                .filter(link -> link.getReviewStatus() == ReviewStatus.APPROVED)
                .map(link -> evidenceById.get(link.getEvidenceId()))
                .filter(java.util.Objects::nonNull)
                .filter(item -> item.getPublicStatus() == EvidenceStatus.APPROVED)
                .filter(item -> !item.getRawContentPublic())
                .count();
        require(approvedDirectEvidence >= question.getEvidenceRequirement()
                        .getMinimumApprovedEvidencePerRequiredClaim(),
                "required claim approved evidence is insufficient: " + claimId);
    }

    /**
     * 校验 ACTIVE 预的问题身份唯一性：把文本（或别名）归一化后加入身份集合，
     * 归一化结果为空或与已有身份重复即失败。
     */
    private static void requireUniqueQuestionIdentity(
            Set<String> identities,
            String text,
            String questionId
    ) {
        String normalized = StableQuestionNormalizer.normalize(text);
        require(hasText(normalized), "active question text identity is required: " + questionId);
        require(identities.add(normalized),
                "active question text identity must be unique: " + normalized);
    }

    /**
     * 判断成果状态：DELIVERED、IMPLEMENTED_TESTED、PROTOTYPE、DESIGNED 视为成果，
     * 此类断言必须有 APPROVED 的 DIRECT 证据支撑。
     */
    private static boolean isAchievement(AchievementStatus status) {
        return status == AchievementStatus.DELIVERED
                || status == AchievementStatus.IMPLEMENTED_TESTED
                || status == AchievementStatus.PROTOTYPE
                || status == AchievementStatus.DESIGNED;
    }

    /** 校验集合字段本身存在（非 null），失败时提示字段名。 */
    private static <T> List<T> requiredList(List<T> value, String field) {
        require(value != null, field + " is required");
        return value;
    }

    /** 校验字符串列表非 null、非空且不含空白项（每个元素必须有实际文本）。 */
    private static List<String> requiredNonBlankList(List<String> value, String field) {
        require(value != null && !value.isEmpty(), field + " must not be empty");
        validateNonBlankValues(value, field);
        return value;
    }

    /**
     * 校验对象的展示关联：projectIds 与 caseIds 都必须存在且不含空白项，
     * 且两者至少有一个非空（对象必须挂在至少一个项目或案例上）。
     */
    private static void requireAssociation(
            List<String> projectIds,
            List<String> caseIds,
            String type
    ) {
        List<String> requiredProjectIds = requiredList(projectIds, type + " projectIds");
        List<String> requiredCaseIds = requiredList(caseIds, type + " caseIds");
        require(!requiredProjectIds.isEmpty() || !requiredCaseIds.isEmpty(),
                type + " must reference at least one project or case");
        validateNonBlankValues(requiredProjectIds, type + " projectIds");
        validateNonBlankValues(requiredCaseIds, type + " caseIds");
    }

    /** 校验列表中每个字符串都不为空白。 */
    private static void validateNonBlankValues(List<String> values, String field) {
        for (String item : values) {
            require(hasText(item), field + " must not contain blank values");
        }
    }

    /** 校验列表元素既不为空白也不重复（用于引用类 ID 列表）。 */
    private static void validateUniqueNonBlankValues(List<String> values, String field) {
        validateNonBlankValues(values, field);
        Set<String> uniqueValues = new HashSet<>();
        for (String value : values) {
            require(uniqueValues.add(value), field + " must not contain duplicate values: " + value);
        }
    }

    /** 校验两个 id 集合不相交（用于项目/案例/合集之间 id、code、slug 的命名空间互斥）。 */
    private static void requireDisjoint(
            Set<String> first,
            Set<String> second,
            String message
    ) {
        require(first.stream().noneMatch(second::contains), message);
    }

    /**
     * 校验列表内对象非 null、按提取器取得的 id 非空且互不重复，并返回 id 到对象的映射
     * 供后续引用存在性检查使用。
     */
    private static <T> Map<String, T> uniqueById(
            List<T> values,
            Function<T, String> idExtractor,
            String type
    ) {
        Map<String, T> byId = new HashMap<>();
        Set<String> seen = new HashSet<>();
        for (T value : values) {
            require(value != null, type + " item must not be null");
            String id = idExtractor.apply(value);
            require(hasText(id), type + " id is required");
            require(seen.add(id), "duplicate " + type + " id: " + id);
            byId.put(id, value);
        }
        return Map.copyOf(byId);
    }

    /** 判断字符串非 null 且去除首尾空白后非空。 */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** 断言工具：条件不成立时抛出携带字段级描述的快照校验异常。 */
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidPortfolioSnapshotException(message);
        }
    }
}
