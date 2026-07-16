package com.portfolio.agent.common.web;

import com.portfolio.agent.common.exception.ApplicationException;
import com.portfolio.agent.common.exception.CommonErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ApiErrorResponse> handleApplicationException(
            ApplicationException exception
    ) {
        HttpStatus status = HttpStatus.valueOf(exception.getErrorCode().getHttpStatus());
        return response(status, exception.getErrorCode().getCode(), exception.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiErrorResponse> handleValidation(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, CommonErrorCode.VALIDATION_ERROR);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException exception) {
        return response(HttpStatus.NOT_FOUND, CommonErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception
    ) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, CommonErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception
    ) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                CommonErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
        String requestId = UUID.randomUUID().toString();
        LOGGER.error("Unexpected server error, requestId={}", requestId);
        return response(
                requestId,
                HttpStatus.INTERNAL_SERVER_ERROR,
                CommonErrorCode.INTERNAL_ERROR.getCode(),
                CommonErrorCode.INTERNAL_ERROR.getDefaultMessage()
        );
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            CommonErrorCode errorCode
    ) {
        return response(status, errorCode.getCode(), errorCode.getDefaultMessage());
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String code,
            String message
    ) {
        return response(UUID.randomUUID().toString(), status, code, message);
    }

    private ResponseEntity<ApiErrorResponse> response(
            String requestId,
            HttpStatus status,
            String code,
            String message
    ) {
        ApiErrorResponse body = new ApiErrorResponse(
                requestId,
                code,
                message,
                OffsetDateTime.now()
        );
        return ResponseEntity.status(status).body(body);
    }
}
