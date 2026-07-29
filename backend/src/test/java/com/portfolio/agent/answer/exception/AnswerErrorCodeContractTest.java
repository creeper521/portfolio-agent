package com.portfolio.agent.answer.exception;

import com.portfolio.agent.common.web.ApiErrorResponse;
import com.portfolio.agent.common.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerErrorCodeContractTest {

    @Test
    void admissionRejectionKeepsPublishedWireContract() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ApiErrorResponse> response = handler.handleApplicationException(
                new AnswerAdmissionRejectedException(
                        AnswerErrorCode.ANSWER_RATE_LIMITED, 17),
                new MockHttpServletRequest());

        ApiErrorResponse body = response.getBody();
        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("17");
        assertThat(body).isNotNull();
        assertThat(body.getRequestId())
                .isEqualTo(response.getHeaders().getFirst("X-Request-Id"));
        assertThat(body.getCode()).isEqualTo("ANSWER_RATE_LIMITED");
        assertThat(body.getMessage())
                .isEqualTo(AnswerErrorCode.ANSWER_RATE_LIMITED.getDefaultMessage());
        assertThat(body.getRetryAfterSeconds()).isEqualTo(17);
    }

    @Test
    void publishedAnswerCodesRemainStable() {
        assertThat(Arrays.stream(AnswerErrorCode.values())
                .map(AnswerErrorCode::getCode))
                .containsExactly(
                        "INVALID_ANSWER_CONTEXT",
                        "ANSWER_RATE_LIMITED",
                        "ANSWER_CONCURRENCY_LIMITED",
                        "ANSWER_REQUEST_TIMEOUT");
    }

    @Test
    void requestTimeoutRemainsServiceUnavailable() {
        assertThat(AnswerErrorCode.ANSWER_REQUEST_TIMEOUT.getHttpStatus())
                .isEqualTo(503);
    }

    @Test
    void answerCodesDoNotClaimSharedResourceCodes() {
        assertThat(Arrays.stream(AnswerErrorCode.values())
                .map(AnswerErrorCode::getCode))
                .doesNotContain("PROJECT_NOT_FOUND", "CASE_NOT_FOUND");
    }
}
