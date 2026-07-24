package com.portfolio.agent.release.benchmark;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.portfolio.agent.answer.domain.RetrievalDecisionType;
import com.portfolio.agent.portfolio.domain.ClaimSubjectType;

import java.util.List;

public final class RetrievalBenchmarkCase {

    private final String caseId;
    private final RetrievalBenchmarkSplit split;
    private final RetrievalBenchmarkCategory category;
    private final ClaimSubjectType subjectType;
    private final String subjectSlug;
    private final String query;
    private final List<String> expectedClaimIds;
    private final List<String> expectedChunkIds;
    private final RetrievalDecisionType expectedDecision;

    @JsonCreator
    public RetrievalBenchmarkCase(
            @JsonProperty("caseId") String caseId,
            @JsonProperty("split") RetrievalBenchmarkSplit split,
            @JsonProperty("category") RetrievalBenchmarkCategory category,
            @JsonProperty("subjectType") ClaimSubjectType subjectType,
            @JsonProperty("subjectSlug") String subjectSlug,
            @JsonProperty("query") String query,
            @JsonProperty("expectedClaimIds") List<String> expectedClaimIds,
            @JsonProperty("expectedChunkIds") List<String> expectedChunkIds,
            @JsonProperty("expectedDecision") RetrievalDecisionType expectedDecision
    ) {
        this.caseId = caseId;
        this.split = split;
        this.category = category;
        this.subjectType = subjectType;
        this.subjectSlug = subjectSlug;
        this.query = query;
        this.expectedClaimIds = List.copyOf(expectedClaimIds);
        this.expectedChunkIds = List.copyOf(expectedChunkIds);
        this.expectedDecision = expectedDecision;
    }

    public String getCaseId() { return caseId; }
    public RetrievalBenchmarkSplit getSplit() { return split; }
    public RetrievalBenchmarkCategory getCategory() { return category; }
    public ClaimSubjectType getSubjectType() { return subjectType; }
    public String getSubjectSlug() { return subjectSlug; }
    public String getQuery() { return query; }
    public List<String> getExpectedClaimIds() { return expectedClaimIds; }
    public List<String> getExpectedChunkIds() { return expectedChunkIds; }
    public RetrievalDecisionType getExpectedDecision() { return expectedDecision; }
}
