package com.portfolio.agent.answer.domain;

public final class RetrievalPolicy {

    private final String version;
    private final int keywordTopK;
    private final int vectorTopK;
    private final int maxChunks;
    private final int maxClaims;
    private final int rrfK;
    private final int maxContextCharacters;
    private final int strongKeywordMinimum;
    private final double vectorCandidateThreshold;
    private final double ambiguityMargin;

    private RetrievalPolicy(
            String version,
            int keywordTopK,
            int vectorTopK,
            int maxChunks,
            int maxClaims,
            int rrfK,
            int maxContextCharacters,
            int strongKeywordMinimum,
            double vectorCandidateThreshold,
            double ambiguityMargin
    ) {
        this.version = version;
        this.keywordTopK = keywordTopK;
        this.vectorTopK = vectorTopK;
        this.maxChunks = maxChunks;
        this.maxClaims = maxClaims;
        this.rrfK = rrfK;
        this.maxContextCharacters = maxContextCharacters;
        this.strongKeywordMinimum = strongKeywordMinimum;
        this.vectorCandidateThreshold = vectorCandidateThreshold;
        this.ambiguityMargin = ambiguityMargin;
    }

    public static RetrievalPolicy firstRelease() {
        return new RetrievalPolicy(
                "retrieval-policy-v1", 8, 8, 12, 8, 60,
                6000, 2, 0.55, 0.05);
    }

    public String getVersion() { return version; }
    public int getKeywordTopK() { return keywordTopK; }
    public int getVectorTopK() { return vectorTopK; }
    public int getMaxChunks() { return maxChunks; }
    public int getMaxClaims() { return maxClaims; }
    public int getRrfK() { return rrfK; }
    public int getMaxContextCharacters() { return maxContextCharacters; }
    public int getStrongKeywordMinimum() { return strongKeywordMinimum; }
    public double getVectorCandidateThreshold() { return vectorCandidateThreshold; }
    public double getAmbiguityMargin() { return ambiguityMargin; }
}
