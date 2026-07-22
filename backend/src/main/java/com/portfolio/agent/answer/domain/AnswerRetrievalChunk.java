package com.portfolio.agent.answer.domain;

import java.util.List;

public final class AnswerRetrievalChunk {

    private final String chunkId;
    private final List<String> projectSlugs;
    private final List<String> claimIds;
    private final List<String> topics;
    private final int textLength;

    public AnswerRetrievalChunk(
            String chunkId,
            List<String> projectSlugs,
            List<String> claimIds,
            List<String> topics,
            int textLength
    ) {
        this.chunkId = chunkId;
        this.projectSlugs = List.copyOf(projectSlugs);
        this.claimIds = List.copyOf(claimIds);
        this.topics = List.copyOf(topics);
        this.textLength = textLength;
    }

    public String getChunkId() { return chunkId; }
    public List<String> getProjectSlugs() { return projectSlugs; }
    public List<String> getClaimIds() { return claimIds; }
    public List<String> getTopics() { return topics; }
    public int getTextLength() { return textLength; }
}
