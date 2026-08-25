package com.portfolio.agent.portfolio.release;

import com.portfolio.agent.portfolio.domain.Claim;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;
import com.portfolio.agent.portfolio.domain.CaseCollection;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
import com.portfolio.agent.portfolio.domain.CaseStudy;
import com.portfolio.agent.portfolio.domain.RagDocument;
import com.portfolio.agent.portfolio.exception.InvalidPortfolioSnapshotException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 把作品集快照中的声明（Claim）编译为 RAG 检索文档的构建器。
 *
 * <p>每个声明生成一个 {@link RagDocument} chunk：文本由主体标题、所属合集上下文与
 * 声明陈述拼接而成，topics 合并声明主题与合集 slug；documentHash 由
 * {@link RagDocumentHashCalculator} 按内容计算。产出按声明 id 排序，保证编译结果确定性。
 *
 * <p>失败行为：快照缺失、发布时间为空、声明主体（项目/案例）或所属合集在快照中不存在时，
 * 抛出 {@link InvalidPortfolioSnapshotException}。
 */
public final class ClaimRagDocumentBuilder {

    /**
     * 以快照发布日期作为文档生效日（validFrom）构建声明 RAG 文档。
     *
     * @param snapshot 已发布的作品集快照
     * @return 按声明 id 排序的 RAG 文档列表
     * @throws InvalidPortfolioSnapshotException 快照为 null 或缺少发布时间
     */
    public List<RagDocument> build(PortfolioSnapshot snapshot) {
        if (snapshot == null || snapshot.getPublishedAt() == null) {
            throw new InvalidPortfolioSnapshotException(
                    "published portfolio snapshot is required");
        }
        return build(snapshot, snapshot.getPublishedAt().toLocalDate());
    }

    /**
     * 以指定生效日构建声明 RAG 文档。
     *
     * <p>先建立项目/案例/合集的 id 索引，再逐声明生成文档，整体按声明 id 排序，
     * 保证同一快照在任意时刻编译结果一致。
     *
     * @param snapshot  作品集快照
     * @param validFrom 文档生效日，写入每个文档的 validFrom 字段
     * @return 排序后的 RAG 文档列表
     * @throws InvalidPortfolioSnapshotException 参数为 null，或声明主体/所属合集缺失
     */
    public List<RagDocument> build(PortfolioSnapshot snapshot, LocalDate validFrom) {
        if (snapshot == null || validFrom == null) {
            throw new InvalidPortfolioSnapshotException(
                    "portfolio snapshot and RAG validFrom are required");
        }
        Map<String, ProjectProfile> projectsById = snapshot.getProjects().stream()
                .collect(Collectors.toUnmodifiableMap(ProjectProfile::getId, Function.identity()));
        Map<String, CaseStudy> casesById = snapshot.getCases().stream()
                .collect(Collectors.toUnmodifiableMap(CaseStudy::getId, Function.identity()));
        Map<String, CaseCollection> collectionsById = snapshot.getCollections().stream()
                .collect(Collectors.toUnmodifiableMap(
                        CaseCollection::getId, Function.identity()));
        return snapshot.getClaims().stream()
                .sorted(java.util.Comparator.comparing(Claim::getId))
                .map(claim -> document(
                        snapshot, projectsById, casesById, collectionsById, claim, validFrom))
                .toList();
    }

    /**
     * 组装单个声明的 RAG 文档。
     *
     * <p>按声明主体类型反查项目或案例；文本形如"主体标题 + 合集上下文：陈述 + 详情"，
     * 先构造未签名文档再补上内容哈希，形成最终文档。
     *
     * @throws InvalidPortfolioSnapshotException 声明主体不存在，或案例引用的合集不存在
     */
    private RagDocument document(
            PortfolioSnapshot snapshot,
            Map<String, ProjectProfile> projectsById,
            Map<String, CaseStudy> casesById,
            Map<String, CaseCollection> collectionsById,
            Claim claim,
            LocalDate validFrom
    ) {
        ProjectProfile project = claim.getSubjectType() == ClaimSubjectType.PROJECT
                ? projectsById.get(claim.getSubjectId())
                : null;
        CaseStudy caseStudy = claim.getSubjectType() == ClaimSubjectType.CASE
                ? casesById.get(claim.getSubjectId())
                : null;
        if (project == null && caseStudy == null) {
            throw new InvalidPortfolioSnapshotException(
                    "claim owner does not exist: " + claim.getId());
        }
        List<String> projectSlugs = project == null
                ? List.of()
                : List.of(project.getSlug());
        List<String> caseSlugs = caseStudy == null
                ? List.of()
                : List.of(caseStudy.getSlug());
        String ownerTitle = project == null ? caseStudy.getTitle() : project.getTitle();
        List<CaseCollection> caseCollections = caseStudy == null
                ? List.of()
                : caseStudy.getCollectionIds().stream()
                        .map(collectionId -> {
                            CaseCollection collection = collectionsById.get(collectionId);
                            if (collection == null) {
                                throw new InvalidPortfolioSnapshotException(
                                        "case collection does not exist: " + collectionId);
                            }
                            return collection;
                        })
                        .toList();
        List<String> topics = java.util.stream.Stream.concat(
                        claim.getTopics().stream(),
                        caseCollections.stream().map(CaseCollection::getSlug)
                )
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .toList();
        String fact = (claim.getStatement().strip() + " " + claim.getDetail().strip()).strip();
        String collectionContext = caseCollections.stream()
                .map(collection -> collection.getTitle().strip()
                        + " " + collection.getSlug().strip())
                .collect(Collectors.joining(" "));
        String subjectContext = (ownerTitle.strip() + " " + collectionContext).strip();
        RagDocument unsigned = new RagDocument(
                "chunk-" + claim.getId(),
                snapshot.getContentVersion(),
                projectSlugs,
                caseSlugs,
                List.of(claim.getId()),
                subjectContext + "：" + fact,
                topics,
                validFrom,
                null,
                "unsigned");
        return new RagDocument(
                unsigned.getChunkId(),
                unsigned.getContentVersion(),
                unsigned.getProjectSlugs(),
                unsigned.getCaseSlugs(),
                unsigned.getClaimIds(),
                unsigned.getText(),
                unsigned.getTopics(),
                unsigned.getValidFrom(),
                unsigned.getValidUntil(),
                RagDocumentHashCalculator.contentHash(unsigned));
    }
}
