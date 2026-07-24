package com.portfolio.agent.answer.domain;

import java.util.List;

public final class AnswerRetrievalChunk {

    private final String chunkId;
    private final List<String> projectSlugs;
    private final List<String> caseSlugs;
    private final List<String> claimIds;
    private final List<String> topics;
    private final String text;
    private final int textLength;

    public AnswerRetrievalChunk(
            String chunkId,
            List<String> projectSlugs,
            List<String> caseSlugs,
            List<String> claimIds,
            List<String> topics,
            String text,
            int textLength
    ) {
        this.chunkId = chunkId;
        this.projectSlugs = List.copyOf(projectSlugs);
        this.caseSlugs = List.copyOf(caseSlugs);
        this.claimIds = List.copyOf(claimIds);
        this.topics = List.copyOf(topics);
        this.text = text;
        this.textLength = textLength;
    }

    public AnswerRetrievalChunk(
            String chunkId,
            List<String> projectSlugs,
            List<String> caseSlugs,
            List<String> claimIds,
            List<String> topics,
            int textLength
    ) {
        this(chunkId, projectSlugs, caseSlugs, claimIds, topics, null, textLength);
    }

    public AnswerRetrievalChunk(
            String chunkId,
            List<String> projectSlugs,
            List<String> claimIds,
            List<String> topics,
            int textLength
    ) {
        this(chunkId, projectSlugs, List.of(), claimIds, topics, null, textLength);
    }

    public String getChunkId() { return chunkId; }
    public List<String> getProjectSlugs() { return projectSlugs; }
    public List<String> getCaseSlugs() { return caseSlugs; }
    public List<String> getClaimIds() { return claimIds; }
    public List<String> getTopics() { return topics; }
    public String getText() { return text; }
    public int getTextLength() { return textLength; }
}
