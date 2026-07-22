package com.portfolio.agent.answer.domain;

public final class RetrievalCandidate {

    private final String chunkId;
    private final Integer keywordRank;
    private final Integer vectorRank;
    private final double fusedScore;

    public RetrievalCandidate(
            String chunkId,
            Integer keywordRank,
            Integer vectorRank,
            double fusedScore
    ) {
        this.chunkId = chunkId;
        this.keywordRank = keywordRank;
        this.vectorRank = vectorRank;
        this.fusedScore = fusedScore;
    }

    public String getChunkId() { return chunkId; }
    public Integer getKeywordRank() { return keywordRank; }
    public Integer getVectorRank() { return vectorRank; }
    public double getFusedScore() { return fusedScore; }
}
