package com.portfolio.agent.portfolio.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

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
    private final String checksumsFile;
    private final BundleCounts counts;

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
            @JsonProperty("checksumsFile") String checksumsFile,
            @JsonProperty("counts") BundleCounts counts) {
        this.schemaVersion = schemaVersion; this.contentVersion = contentVersion;
        this.publishedAt = publishedAt; this.builtAt = builtAt;
        this.minimumApplicationVersion = minimumApplicationVersion; this.factsFile = factsFile;
        this.presentationFile = presentationFile; this.approvalId = approvalId;
        this.approvalDigest = approvalDigest; this.candidatePayloadHash = candidatePayloadHash;
        this.checksumsFile = checksumsFile; this.counts = counts;
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
    public String getChecksumsFile() { return checksumsFile; }
    public BundleCounts getCounts() { return counts; }
}
