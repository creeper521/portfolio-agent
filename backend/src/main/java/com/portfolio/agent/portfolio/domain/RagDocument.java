package com.portfolio.agent.portfolio.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

public final class RagDocument {

    private final String chunkId;
    private final String contentVersion;
    private final List<String> projectSlugs;
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
        this.claimIds = List.copyOf(claimIds);
        this.text = text;
        this.topics = List.copyOf(topics);
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.contentHash = contentHash;
    }

    public String getChunkId() { return chunkId; }
    public String getContentVersion() { return contentVersion; }
    public List<String> getProjectSlugs() { return projectSlugs; }
    public List<String> getClaimIds() { return claimIds; }
    public String getText() { return text; }
    public List<String> getTopics() { return topics; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getValidUntil() { return validUntil; }
    public String getContentHash() { return contentHash; }
}
