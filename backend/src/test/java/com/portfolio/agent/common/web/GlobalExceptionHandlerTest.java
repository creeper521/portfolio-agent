package com.portfolio.agent.common.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.agent.common.exception.ApplicationException;
import com.portfolio.agent.common.exception.CommonErrorCode;
import com.portfolio.agent.common.exception.ErrorCode;
import com.portfolio.agent.common.observability.DiagnosticEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<DiagnosticEvent> events = new ArrayList<>();
    private FailureController failureController;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        events.clear();
        RequestContextHolder.clear();
        failureController = new FailureController();
        mockMvc = MockMvcBuilders.standaloneSetup(failureController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestDiagnosticsFilter(events::add))
                .build();
    }

    @Test
    void unexpectedErrorUsesActiveRequestIdWithoutLeakingMessage() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/unexpected")
                        .header("X-Client-Request-Id",
                                "550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value(CommonErrorCode.INTERNAL_ERROR.getDefaultMessage()))
                .andExpect(content().string(not(containsString("SECRET_EXCEPTION_MESSAGE"))))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String responseRequestId = result.getResponse().getHeader("X-Request-Id");
        assertThat(body.get("requestId").asText()).isEqualTo(responseRequestId);
        assertThat(failureController.getActiveRequestId()).isEqualTo(responseRequestId);

        assertThat(events).extracting(DiagnosticEvent::getName)
                .containsExactly("http.request.started", "http.request.failed");
        DiagnosticEvent failure = events.get(1);
        assertThat(failure.getFields())
                .containsEntry("error.code", "INTERNAL_ERROR")
                .containsEntry("failure.exception_type", IllegalStateException.class.getName())
                .containsEntry("request.id", responseRequestId);
        assertThat(failure.getFields().get("failure.frames").toString())
                .doesNotContain("SECRET_EXCEPTION_MESSAGE");
    }

    @Test
    void unexpectedErrorStillReturnsSafeResponseWhenRendererFails() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpected(
                new StackTraceFailureException(),
                new MockHttpServletRequest());

        assertSafeInternalErrorResponse(response);
    }

    @Test
    void unexpectedErrorStillReturnsSafeResponseWhenFailureAttributeCannotBeSet() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpected(
                new IllegalStateException("SECRET_EXCEPTION_MESSAGE"),
                new AttributeFailureRequest());

        assertSafeInternalErrorResponse(response);
    }

    @Test
    void expectedApplicationErrorKeepsWireContractWhenDiagnosticsAttributeCannotBeSet() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ApiErrorResponse> response;
        try {
            response = handler.handleApplicationException(
                    new RetryableTestException(17),
                    new AttributeFailureRequest());
        } catch (RuntimeException diagnosticsFailure) {
            fail("diagnostics failure must not replace the application error response",
                    diagnosticsFailure);
            return;
        }

        ApiErrorResponse body = response.getBody();
        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("17");
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo("TEST_RATE_LIMITED");
        assertThat(body.getMessage()).isEqualTo("retry later");
        assertThat(body.getRetryAfterSeconds()).isEqualTo(17);
    }

    @Test
    void errorWithoutActiveContextUsesOneFallbackIdForBodyAndHeader() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ApiErrorResponse> response = handler.handleUnexpected(
                new IllegalStateException("SECRET_EXCEPTION_MESSAGE"),
                new MockHttpServletRequest());

        assertSafeInternalErrorResponse(response);
    }

    @Test
    void expectedApplicationErrorKeepsWireContractAndPublishesRejectedCode() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/expected"))
                .andExpect(status().isNotFound())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("expected public message"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("requestId").asText())
                .isEqualTo(result.getResponse().getHeader("X-Request-Id"));
        assertThat(events).extracting(DiagnosticEvent::getName)
                .containsExactly("http.request.started", "http.request.rejected");
        assertThat(events.get(1).getFields()).containsEntry("error.code", "NOT_FOUND");
    }

    @Test
    void responseStatusExceptionKeepsItsPublicStatusAndStableReasonCode() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));

        assertThat(events).extracting(DiagnosticEvent::getName)
                .containsExactly("http.request.started", "http.request.rejected");
        assertThat(events.get(1).getFields())
                .containsEntry("error.code", "IDEMPOTENCY_KEY_CONFLICT");
    }

    @Test
    void rateLimitResponseKeepsRetryAfterHeaderAndBody() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/rate-limited"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "17"))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("TEST_RATE_LIMITED"))
                .andExpect(jsonPath("$.message").value("retry later"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(17))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("requestId").asText())
                .isEqualTo(result.getResponse().getHeader("X-Request-Id"));
        assertThat(events).extracting(DiagnosticEvent::getName)
                .containsExactly("http.request.started", "http.request.rejected");
        assertThat(events.get(1).getFields())
                .containsEntry("error.code", "TEST_RATE_LIMITED");
    }

    @Test
    void errorBodyCanExposeOnlySafeRetryDelay() {
        ApiErrorResponse response = new ApiErrorResponse(
                "request-id",
                "ANSWER_RATE_LIMITED",
                "请求过于频繁，请稍后再试。",
                12,
                OffsetDateTime.parse("2026-07-28T00:00:00Z"));

        assertThat(response.getRetryAfterSeconds()).isEqualTo(12);
        assertThat(response.toString())
                .doesNotContain("visitor question", "203.0.113.7", "request-token");
    }

    private void assertSafeInternalErrorResponse(ResponseEntity<ApiErrorResponse> response) {
        ApiErrorResponse body = response.getBody();
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(body).isNotNull();
        assertThat(body.getCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(body.getMessage()).isEqualTo(CommonErrorCode.INTERNAL_ERROR.getDefaultMessage());
        assertThat(body.getRequestId())
                .isNotBlank()
                .isEqualTo(response.getHeaders().getFirst("X-Request-Id"));
        assertThat(body.toString())
                .doesNotContain(
                        "SECRET_EXCEPTION_MESSAGE",
                        "SECRET_RENDERER_FAILURE",
                        "SECRET_ATTRIBUTE_FAILURE");
    }

    @RestController
    static final class FailureController {

        private String activeRequestId;

        @GetMapping("/test/unexpected")
        String unexpected() {
            activeRequestId = RequestContextHolder.requireCurrent().getRequestId();
            throw new IllegalStateException("SECRET_EXCEPTION_MESSAGE");
        }

        @GetMapping("/test/expected")
        String expected() {
            throw new ApplicationException(
                    CommonErrorCode.NOT_FOUND, "expected public message");
        }

        @GetMapping("/test/rate-limited")
        String rateLimited() {
            throw new RetryableTestException(17);
        }

        @GetMapping("/test/conflict")
        String conflict() {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "IDEMPOTENCY_KEY_CONFLICT");
        }

        String getActiveRequestId() {
            return activeRequestId;
        }
    }

    static final class RetryableTestException extends ApplicationException {

        RetryableTestException(int retryAfterSeconds) {
            super(
                    TestErrorCode.RATE_LIMITED,
                    TestErrorCode.RATE_LIMITED.getDefaultMessage(),
                    retryAfterSeconds);
        }
    }

    enum TestErrorCode implements ErrorCode {

        RATE_LIMITED("TEST_RATE_LIMITED", "retry later", 429);

        private final String code;
        private final String defaultMessage;
        private final int httpStatus;

        TestErrorCode(String code, String defaultMessage, int httpStatus) {
            this.code = code;
            this.defaultMessage = defaultMessage;
            this.httpStatus = httpStatus;
        }

        @Override
        public String getCode() {
            return code;
        }

        @Override
        public String getDefaultMessage() {
            return defaultMessage;
        }

        @Override
        public int getHttpStatus() {
            return httpStatus;
        }
    }

    static final class StackTraceFailureException extends RuntimeException {

        StackTraceFailureException() {
            super("SECRET_EXCEPTION_MESSAGE");
        }

        @Override
        public StackTraceElement[] getStackTrace() {
            throw new IllegalStateException("SECRET_RENDERER_FAILURE");
        }
    }

    static final class AttributeFailureRequest extends MockHttpServletRequest {

        @Override
        public void setAttribute(String name, Object value) {
            throw new IllegalStateException("SECRET_ATTRIBUTE_FAILURE");
        }
    }
}
