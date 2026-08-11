package com.portfolio.agent.answer.routing.service;

import com.portfolio.agent.answer.routing.domain.SemanticTurnInput;

/** The only phase-two semantic routing entry point. */
public interface TurnRouter {

    SemanticTurnDecision route(SemanticTurnInput input);
}
