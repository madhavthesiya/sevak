package com.sevak.api.common;

import java.net.URI;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Turns every failure into an RFC 9457 problem response with a stable machine-readable type. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String TYPE_PREFIX = "urn:sevak:error:";

    private static ProblemDetail problem(HttpStatusCode status, String type, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + type));
        return problem;
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    ProblemDetail unauthorized(UnauthorizedException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "unauthenticated", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, ex.type(), ex.getMessage());
    }

    @ExceptionHandler(UnprocessableException.class)
    ProblemDetail unprocessable(UnprocessableException ex) {
        return problem(HttpStatusCode.valueOf(422), ex.type(), ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "validation-error", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex) {
        log.error("Unhandled error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal", "Something went wrong");
    }

    /** Failed @Min, @Max, @Size and @Pattern checks on query parameters. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detail = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error ->
                                result.getMethodParameter().getParameterName() + ": " + error.getDefaultMessage()))
                .collect(Collectors.joining("; "));
        return handleExceptionInternal(ex, problem(status, "validation-error", detail), headers, status, request);
    }

    /** Failed checks on a JSON request body. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return handleExceptionInternal(ex, problem(status, "validation-error", detail), headers, status, request);
    }

    /** Gives Spring's own errors (404, 405, bad types) a type as well. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, status, request);
        if (response != null
                && response.getBody() instanceof ProblemDetail problem
                && (problem.getType() == null || URI.create("about:blank").equals(problem.getType()))) {
            problem.setType(URI.create(TYPE_PREFIX + typeOf(status)));
        }
        return response;
    }

    private static String typeOf(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "validation-error";
            case 404 -> "not-found";
            case 405 -> "method-not-allowed";
            default -> "http-error";
        };
    }
}
