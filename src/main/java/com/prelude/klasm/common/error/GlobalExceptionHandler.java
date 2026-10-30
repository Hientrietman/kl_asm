package com.prelude.klasm.common.error;

import com.prelude.klasm.common.web.CurrentTraceId;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Converts every error into RFC 9457 Problem Details with a catalog {@code code} and the {@code traceId}. Details
 * never contain exception messages from libraries, so no SQL, stack trace or class name can leak.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final CurrentTraceId currentTraceId;

    GlobalExceptionHandler(CurrentTraceId currentTraceId) {
        this.currentTraceId = currentTraceId;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> handleApiException(ApiException ex, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(ex.code().status(), ex.getMessage());
        problemDetail.setProperty("code", ex.code().name());
        ex.properties().forEach(problemDetail::setProperty);
        return handleExceptionInternal(
                ex, problemDetail, new HttpHeaders(), ex.code().status(), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        List<FieldViolation> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldViolation(leafName(v), v.getMessage()))
                .toList();
        return problem(ex, ErrorCode.VALIDATION_ERROR, "Request validation failed.", errors, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unhandled exception", ex);
        return problem(ex, ErrorCode.INTERNAL_ERROR, "An unexpected error occurred.", List.of(), request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = new ArrayList<>();
        ex.getBindingResult()
                .getFieldErrors()
                .forEach(e -> errors.add(new FieldViolation(e.getField(), e.getDefaultMessage())));
        ex.getBindingResult()
                .getGlobalErrors()
                .forEach(e -> errors.add(new FieldViolation(e.getObjectName(), e.getDefaultMessage())));
        return problem(ex, ErrorCode.VALIDATION_ERROR, "Request validation failed.", errors, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String parameter = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                String field = error instanceof FieldError fieldError ? fieldError.getField() : parameter;
                errors.add(new FieldViolation(field, error.getDefaultMessage()));
            }
        });
        return problem(ex, ErrorCode.VALIDATION_ERROR, "Request validation failed.", errors, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problem(ex, ErrorCode.VALIDATION_ERROR, "Malformed request body.", List.of(), request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String name = ex.getPropertyName() == null ? "parameter" : ex.getPropertyName();
        List<FieldViolation> errors = List.of(new FieldViolation(name, "has an invalid format"));
        return problem(ex, ErrorCode.VALIDATION_ERROR, "Request validation failed.", errors, request);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problem(ex, ErrorCode.NOT_FOUND, "Resource not found.", List.of(), request);
    }

    /** Adds {@code code} and {@code traceId} to Problem Details built by the framework (405, 415, 400, ...). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        // Most framework handlers pass a null body that the superclass would only build afterwards
        if (body == null && ex instanceof ErrorResponse errorResponse) {
            body = errorResponse.updateAndGetBody(getMessageSource(), LocaleContextHolder.getLocale());
        }
        if (body instanceof ProblemDetail problemDetail) {
            Object code = problemDetail.getProperties() == null
                    ? null
                    : problemDetail.getProperties().get("code");
            if (code == null) {
                ErrorCode fallback = fallbackCode(statusCode);
                if (fallback != null) {
                    problemDetail.setProperty("code", fallback.name());
                }
            }
            problemDetail.setProperty("traceId", currentTraceId.get());
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private ResponseEntity<Object> problem(
            Exception ex, ErrorCode code, String detail, List<FieldViolation> errors, WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problemDetail.setProperty("code", code.name());
        if (!errors.isEmpty()) {
            problemDetail.setProperty("errors", errors);
        }
        return handleExceptionInternal(ex, problemDetail, new HttpHeaders(), code.status(), request);
    }

    private static ErrorCode fallbackCode(HttpStatusCode status) {
        if (status.value() == HttpStatus.BAD_REQUEST.value()) {
            return ErrorCode.VALIDATION_ERROR;
        }
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return ErrorCode.NOT_FOUND;
        }
        if (status.is5xxServerError()) {
            return ErrorCode.INTERNAL_ERROR;
        }
        return null;
    }

    private static String leafName(ConstraintViolation<?> violation) {
        String name = null;
        for (Path.Node node : violation.getPropertyPath()) {
            name = node.getName();
        }
        return name;
    }
}
