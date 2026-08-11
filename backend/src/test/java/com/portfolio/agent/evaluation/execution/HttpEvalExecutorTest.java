package com.portfolio.agent.evaluation.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.answer.domain.AnswerResolution;
import com.portfolio.agent.answer.domain.AnswerSource;
import com.portfolio.agent.answer.domain.ConversationAnswerScope;
import com.portfolio.agent.answer.domain.GenerationMode;
import com.portfolio.agent.evaluation.domain.EvalAnswerShape;
import com.portfolio.agent.evaluation.domain.EvalLayer;
import com.portfolio.agent.evaluation.domain.EvalMessage;
import com.portfolio.agent.evaluation.domain.EvalObservation;
import com.portfolio.agent.evaluation.domain.EvalObservationStatus;
import com.portfolio.agent.evaluation.domain.EvalSemanticTurnShape;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HttpEvalExecutorTest {

    @Test
    void validConfirmationTurnIsEvaluatedAsAnApiResponseNotAnAnswerFailure() throws Exception {
        EvalSemanticTurnShape shape = EvalSemanticTurnShape.from(
                new ObjectMapper().readTree("""
                        {"disposition":"CONFIRMATION_REQUIRED","plan":{"taskCount":2,"tasks":[
                          {"sourceDomain":"PORTFOLIO"},
                          {"sourceDomain":"GENERAL","dependencySummary":"requires 01"}
                        ]}}
                        """));
        EvalAnswerClient client = request -> new EvalHttpResult(
                200, AnswerResolution.AWAITING_CONFIRMATION,
                ConversationAnswerScope.PORTFOLIO, GenerationMode.DETERMINISTIC,
                AnswerSource.RETRIEVAL, null, null, List.of(), List.of(), false,
                null, 1L, EvalHttpResult.FailureCode.NONE, EvalAnswerShape.empty(), shape);
        HttpEvalExecutor executor = new HttpEvalExecutor(client, "http://example.test");

        EvalObservation observation = executor.execute(new EvalExecutionInput(
                "semantic.confirmation", List.of(new EvalMessage("user", "compare then recommend")),
                EvalLayer.HTTP_E2E, 1), new EvalRunContext("run", "content"));

        assertThat(observation.getStatus()).isEqualTo(EvalObservationStatus.PASS);
        assertThat(observation.getReasonCodes()).containsExactly("HTTP_SEMANTIC_TURN");
        assertThat(observation.getSemanticTurnShape().getTaskCount()).isEqualTo(2);
    }
}
