package com.portfolio.agent.portfolio.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

/**
 * 检索文档：公开快照内容切块后的检索单元（chunk）。
 *
 * <p>text 是由已审校公开内容生成的检索文本；projectSlugs/caseSlugs 记录切块回链的
 * 展示对象；claimIds 记录来源断言；validFrom/validUntil 描述内容有效期（如实习时间段）；
 * contentHash 锁定文本内容，用于构建与加载两侧的一致性校验。兼容构造器省略
 * caseSlugs（回退为空列表），供旧检索产物过渡。
 */
public final class RagDocument {

    private final String chunkId;
    private final String contentVersion;
    private final List<String> projectSlugs;
    private final List<String> caseSlugs;
    private final List<String> claimIds;
    private final String text;
    private final List<String> topics;
    private final LocalDate validFrom;
    private final LocalDate validUntil;
    private final String contentHash;

    @JsonCreator
    public RagDocument(
            @JsonProperty("chunkId") String chunkId,
            @JsonProperty("contentVersion") String contentVersion,
            @JsonProperty("projectSlugs") List<String> projectSlugs,
            @JsonProperty("caseSlugs") List<String> caseSlugs,
            @JsonProperty("claimIds") List<String> claimIds,
            @JsonProperty("text") String text,
            @JsonProperty("topics") List<String> topics,
            @JsonProperty("validFrom") LocalDate validFrom,
            @JsonProperty("validUntil") LocalDate validUntil,
            @JsonProperty("contentHash") String contentHash
    ) {
        this.chunkId = chunkId;
        this.contentVersion = contentVersion;
        this.projectSlugs = List.copyOf(projectSlugs);
        this.caseSlugs = caseSlugs == null ? List.of() : List.copyOf(caseSlugs);
        this.claimIds = List.copyOf(claimIds);
        this.text = text;
        this.topics = List.copyOf(topics);
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.contentHash = contentHash;
    }

    public RagDocument(
            String chunkId,
            String contentVersion,
            List<String> projectSlugs,
            List<String> claimIds,
            String text,
            List<String> topics,
            LocalDate validFrom,
            LocalDate validUntil,
            String contentHash
    ) {
        this(chunkId, contentVersion, projectSlugs, List.of(), claimIds, text,
                topics, validFrom, validUntil, contentHash);
    }

    public String getChunkId() { return chunkId; }
    public String getContentVersion() { return contentVersion; }
    public List<String> getProjectSlugs() { return projectSlugs; }
    public List<String> getCaseSlugs() { return caseSlugs; }
    public List<String> getClaimIds() { return claimIds; }
    public String getText() { return text; }
    public List<String> getTopics() { return topics; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getValidUntil() { return validUntil; }
    public String getContentHash() { return contentHash; }
}
