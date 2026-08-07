package com.portfolio.agent.evaluation.grading;

import com.portfolio.agent.evaluation.domain.EvalCase;
import com.portfolio.agent.evaluation.domain.EvalObservation;

import java.util.List;

public interface EvalGrader {

    List<EvalGrade> grade(EvalCase evalCase, EvalObservation observation);
}
