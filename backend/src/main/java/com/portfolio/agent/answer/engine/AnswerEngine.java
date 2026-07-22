package com.portfolio.agent.answer.engine;

import com.portfolio.agent.answer.domain.GeneratedAnswer;
import com.portfolio.agent.answer.domain.ResolvedAnswerContext;

public interface AnswerEngine {

    GeneratedAnswer answer(ResolvedAnswerContext context);
}
