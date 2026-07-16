package com.portfolio.agent.answer.application;

import com.portfolio.agent.answer.domain.model.AnswerResult;

public interface AnswerEngine {

    AnswerResult answer(String projectSlug, String question);
}
