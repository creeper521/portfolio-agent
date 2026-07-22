package com.portfolio.agent.answer.domain;

import java.util.List;

public final class ResolvedAnswerContext {

    private final AnswerTurnSnapshot turnSnapshot;
    private final AnswerKnowledge project;
    private final AnswerQuestion questionPreset;
    private final List<AnswerEvidence> approvedEvidence;

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
    }

    public AnswerTurnSnapshot getTurnSnapshot() { return turnSnapshot; }
    public AnswerKnowledge getProject() { return project; }
    public AnswerQuestion getQuestionPreset() { return questionPreset; }
    public List<AnswerEvidence> getApprovedEvidence() { return approvedEvidence; }
}
