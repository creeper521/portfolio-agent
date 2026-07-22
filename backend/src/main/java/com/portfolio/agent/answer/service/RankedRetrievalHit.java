package com.portfolio.agent.answer.service;

public final class RankedRetrievalHit {

    private final String chunkId;
    private final int rank;
    private final double score;

    public RankedRetrievalHit(String chunkId, int rank, double score) {
        this.chunkId = chunkId;
        this.rank = rank;
        this.score = score;
    }

    public String getChunkId() { return chunkId; }
    public int getRank() { return rank; }
    public double getScore() { return score; }
}
