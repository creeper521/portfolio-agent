package com.portfolio.agent.answer.domain;

import java.util.List;

public final class ResolvedAnswerContext {

    private final AnswerTurnSnapshot turnSnapshot;
    private final AnswerKnowledge project;
    private final AnswerQuestion questionPreset;
    private final List<AnswerEvidence> approvedEvidence;
    private final List<AnswerClaimProjection> selectedClaims;
    private final String canonicalIntent;
    private final AnswerSource answerSource;

    public ResolvedAnswerContext(
            AnswerTurnSnapshot turnSnapshot,
            AnswerKnowledge project,
            AnswerQuestion questionPreset,
            List<AnswerEvidence> approvedEvidence
    ) {
        this.turnSnapshot = turnSnapshot;
        this.project = project;
        this.questionPreset = questionPreset;
        this.approvedEvidence = List.copyOf(approvedEvidence);
        this.selectedClaims = List.copyOf(project.getClaims());
        this.canonicalIntent = questionPreset == null ? null : questionPreset.getCanonicalQuestion();
        this.answerSource = questionPreset == null ? null : AnswerSource.PRESET;
    }

    private ResolvedAnswerContext(
            AnswerTurnSnapshot turnSnapshot,
            AnswerKnowledge project,
            String canonicalIntent,
            List<AnswerClaimProjection> selectedClaims,
            List<AnswerEvidence> approvedEvidence
    ) {
        this.turnSnapshot = turnSnapshot;
        this.project = project;
        this.questionPreset = null;
        this.approvedEvidence = List.copyOf(approvedEvidence);
        this.selectedClaims = List.copyOf(selectedClaims);
        this.canonicalIntent = canonicalIntent;
        this.answerSource = AnswerSource.RETRIEVAL;
    }

    public static ResolvedAnswerContext forRetrieval(
            AnswerTurnSnapshot turnSnapshot,
            AnswerKnowledge project,
            String canonicalIntent,
            List<AnswerClaimProjection> selectedClaims,
            List<AnswerEvidence> approvedEvidence
    ) {
        return new ResolvedAnswerContext(
                turnSnapshot, project, canonicalIntent, selectedClaims, approvedEvidence);
    }

    public AnswerTurnSnapshot getTurnSnapshot() { return turnSnapshot; }
    public AnswerKnowledge getProject() { return project; }
    public AnswerQuestion getQuestionPreset() { return questionPreset; }
    public List<AnswerEvidence> getApprovedEvidence() { return approvedEvidence; }
    public List<AnswerClaimProjection> getSelectedClaims() { return selectedClaims; }
    public String getCanonicalIntent() { return canonicalIntent; }
    public AnswerSource getAnswerSource() { return answerSource; }
}
