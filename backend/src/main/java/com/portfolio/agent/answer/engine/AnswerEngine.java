package com.portfolio.agent.answer.engine;

import com.portfolio.agent.answer.domain.GeneratedAnswer;
import com.portfolio.agent.answer.domain.AnswerPlan;

public interface AnswerEngine {

    GeneratedAnswer answer(AnswerPlan plan);
}
