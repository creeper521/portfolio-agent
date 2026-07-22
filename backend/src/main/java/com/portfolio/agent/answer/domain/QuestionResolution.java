package com.portfolio.agent.answer.domain;

public final class QuestionResolution {

    private final AnswerResolution resolution;
    private final AnswerKnowledge project;
    private final AnswerQuestion questionPreset;

    public QuestionResolution(
            AnswerResolution resolution,
            AnswerKnowledge project,
            AnswerQuestion questionPreset
    ) {
        this.resolution = resolution;
        this.project = project;
        this.questionPreset = questionPreset;
    }

    public AnswerResolution getResolution() {
        return resolution;
    }

    public AnswerKnowledge getProject() {
        return project;
    }

    public AnswerQuestion getQuestionPreset() {
        return questionPreset;
    }
}
