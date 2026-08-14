package com.portfolio.agent.common.web;

import com.portfolio.agent.common.exception.ApplicationException;
import com.portfolio.agent.common.exception.CommonErrorCode;
import com.portfolio.agent.common.observability.SafeExceptionRenderer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final SafeExceptionRenderer renderer = new SafeExceptionRenderer();

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiErrorResponse> handleApplicationException(
            ApplicationException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.valueOf(exception.getErrorCode().getHttpStatus());
        markRejected(request, exception.getErrorCode().getCode());
        return response(
                status,
                exception.getErrorCode().getCode(),
                exception.getMessage(),
                exception.getRetryAfterSeconds());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiErrorResponse> handleValidation(
            Exception exception,
            HttpServletRequest request
    ) {
        markRejected(request, CommonErrorCode.VALIDATION_ERROR.getCode());
        return response(HttpStatus.BAD_REQUEST, CommonErrorCode.VALIDATION_ERROR);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        markRejected(request, CommonErrorCode.NOT_FOUND.getCode());
        return response(HttpStatus.NOT_FOUND, CommonErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        markRejected(request, CommonErrorCode.METHOD_NOT_ALLOWED.getCode());
        return response(HttpStatus.METHOD_NOT_ALLOWED, CommonErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request
    ) {
        markRejected(request, CommonErrorCode.UNSUPPORTED_MEDIA_TYPE.getCode());
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                CommonErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(
            ResponseStatusException exception,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String code = stableStatusCode(exception, status);
        markRejected(request, code);
        return response(status, code, publicStatusMessage(status));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        try {
            request.setAttribute(
                    RequestDiagnosticsFilter.FAILURE_ATTRIBUTE,
                    new RequestFailure(
                            CommonErrorCode.INTERNAL_ERROR.getCode(),
                            exception.getClass().getName(),
                            renderer.render(exception)));
        } catch (RuntimeException diagnosticsFailure) {
            // Diagnostics are best effort and never replace the safe error response.
        }
        return response(HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            CommonErrorCode errorCode
    ) {
        return response(status, errorCode.getCode(), errorCode.getDefaultMessage());
    }

    private String stableStatusCode(ResponseStatusException exception, HttpStatus status) {
        String reason = exception.getReason();
        if (reason != null && reason.matches("[A-Z][A-Z0-9_]{2,63}")) {
            return reason;
        }
        return "HTTP_" + status.value();
    }

    private String publicStatusMessage(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "请求参数不符合要求";
            case CONFLICT -> "请求状态冲突，请检查后重试";
            default -> status.getReasonPhrase();
        };
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String code,
            String message
    ) {
        return response(status, code, message, null);
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String code,
            String message,
            Integer retryAfterSeconds
    ) {
        String requestId = currentRequestId();
        ApiErrorResponse body = new ApiErrorResponse(
                requestId,
                code,
                message,
                retryAfterSeconds,
                OffsetDateTime.now()
        );
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status)
                .header("X-Request-Id", requestId);
        if (retryAfterSeconds != null) {
            response.header("Retry-After", Integer.toString(retryAfterSeconds));
        }
        return response.body(body);
    }

    private void markRejected(HttpServletRequest request, String errorCode) {
        try {
            request.setAttribute(RequestDiagnosticsFilter.ERROR_CODE_ATTRIBUTE, errorCode);
        } catch (RuntimeException diagnosticsFailure) {
            // Diagnostics are best effort and never replace the safe error response.
        }
    }

    private String currentRequestId() {
        return RequestContextHolder.current()
                .map(RequestContext::getRequestId)
                .orElseGet(() -> UUID.randomUUID().toString());
    }
}
