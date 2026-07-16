package com.portfolio.agent.answer.engine;

import com.portfolio.agent.answer.domain.AnswerKnowledge;
import com.portfolio.agent.answer.domain.AnswerResult;

public interface AnswerEngine {

    AnswerResult answer(AnswerKnowledge knowledge, String question);
}
