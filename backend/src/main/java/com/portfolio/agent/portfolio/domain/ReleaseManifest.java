package com.portfolio.agent.portfolio.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/**
 * 发布清单：公开发布包 manifest 文件的结构，描述包内内容的版本、审批与完整性信息。
 *
 * <p>关键字段：schemaVersion/contentVersion/publishedAt/builtAt 标识内容与构建版本；
 * minimumApplicationVersion 是可安全读取该包的最低应用版本；factsFile/presentationFile/
 * checksumsFile 指向包内文件；approvalId/approvalDigest 记录治理审批；candidatePayloadHash
 * 与 ledgerHash 锁定候选载荷与审批台账；presetContractSetHash 锁定 ACTIVE 预设契约集；
 * counts 记录各领域对象条数（加载时与快照实际条数核对）；retrieval 描述随包检索产物。
 */
public final class ReleaseManifest {
    private final String schemaVersion;
    private final String contentVersion;
    private final OffsetDateTime publishedAt;
    private final OffsetDateTime builtAt;
    private final String minimumApplicationVersion;
    private final String factsFile;
    private final String presentationFile;
    private final String approvalId;
    private final String approvalDigest;
    private final String candidatePayloadHash;
    private final String ledgerHash;
    private final String presetContractSetHash;
    private final String checksumsFile;
    private final BundleCounts counts;
    private final RetrievalManifest retrieval;

    @JsonCreator
    public ReleaseManifest(@JsonProperty("schemaVersion") String schemaVersion,
            @JsonProperty("contentVersion") String contentVersion,
            @JsonProperty("publishedAt") OffsetDateTime publishedAt,
            @JsonProperty("builtAt") OffsetDateTime builtAt,
            @JsonProperty("minimumApplicationVersion") String minimumApplicationVersion,
            @JsonProperty("factsFile") String factsFile,
            @JsonProperty("presentationFile") String presentationFile,
            @JsonProperty("approvalId") String approvalId,
            @JsonProperty("approvalDigest") String approvalDigest,
            @JsonProperty("candidatePayloadHash") String candidatePayloadHash,
            @JsonProperty("ledgerHash") String ledgerHash,
            @JsonProperty("presetContractSetHash") String presetContractSetHash,
            @JsonProperty("checksumsFile") String checksumsFile,
            @JsonProperty("counts") BundleCounts counts,
            @JsonProperty("retrieval") RetrievalManifest retrieval) {
        this.schemaVersion = schemaVersion; this.contentVersion = contentVersion;
        this.publishedAt = publishedAt; this.builtAt = builtAt;
        this.minimumApplicationVersion = minimumApplicationVersion; this.factsFile = factsFile;
        this.presentationFile = presentationFile; this.approvalId = approvalId;
        this.approvalDigest = approvalDigest; this.candidatePayloadHash = candidatePayloadHash;
        this.ledgerHash = ledgerHash; this.presetContractSetHash = presetContractSetHash;
        this.checksumsFile = checksumsFile; this.counts = counts;
        this.retrieval = retrieval;
    }
    /**
     * 兼容构造器：省略 ledgerHash 与 presetContractSetHash（均置 null），
     * 供旧版 manifest（尚未引入这两个哈希字段）反序列化或构造时使用。
     */
    public ReleaseManifest(String schemaVersion,
            String contentVersion,
            OffsetDateTime publishedAt,
            OffsetDateTime builtAt,
            String minimumApplicationVersion,
            String factsFile,
            String presentationFile,
            String approvalId,
            String approvalDigest,
            String candidatePayloadHash,
            String checksumsFile,
            BundleCounts counts,
            RetrievalManifest retrieval) {
        this(schemaVersion, contentVersion, publishedAt, builtAt,
                minimumApplicationVersion, factsFile, presentationFile,
                approvalId, approvalDigest, candidatePayloadHash, null, null,
                checksumsFile, counts, retrieval);
    }
    public String getSchemaVersion() { return schemaVersion; }
    public String getContentVersion() { return contentVersion; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public OffsetDateTime getBuiltAt() { return builtAt; }
    public String getMinimumApplicationVersion() { return minimumApplicationVersion; }
    public String getFactsFile() { return factsFile; }
    public String getPresentationFile() { return presentationFile; }
    public String getApprovalId() { return approvalId; }
    public String getApprovalDigest() { return approvalDigest; }
    public String getCandidatePayloadHash() { return candidatePayloadHash; }
    public String getLedgerHash() { return ledgerHash; }
    public String getPresetContractSetHash() { return presetContractSetHash; }
    public String getChecksumsFile() { return checksumsFile; }
    public BundleCounts getCounts() { return counts; }
    public RetrievalManifest getRetrieval() { return retrieval; }
}
