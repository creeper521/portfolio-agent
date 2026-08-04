package com.portfolio.agent.evaluation.execution;

import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalObservation;

public interface EvalExecutor {

    boolean supports(EvalLayer layer);

    EvalObservation execute(EvalExecutionInput input, EvalRunContext context);
}
