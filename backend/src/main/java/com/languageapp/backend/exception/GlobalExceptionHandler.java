package com.languageapp.backend.exception;

import com.languageapp.backend.exception.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.util.HtmlUtils;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.stream.Collectors;
import java.time.Clock;

/**
 * Global exception handler for REST controllers.
 * <p>
 * Translates application-specific and generic exceptions
 * into structured HTTP error responses.
 * Ensures consistent API error format across the application.
 */
@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {
    private final Clock clock;

    @ExceptionHandler({UploadTooLargeException.class, org.springframework.web.multipart.MaxUploadSizeExceededException.class})
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(413).body(buildErrorResponse(
                HttpStatus.valueOf(413), "A profilkép legfeljebb 5 MB méretű lehet.", request.getRequestURI()));
    }

    /** Method-security denials must retain 403 rather than becoming generic server errors. */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(buildErrorResponse(
                HttpStatus.FORBIDDEN, "Nincs jogosultságod ehhez a művelethez.", request.getRequestURI()));
    }

    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.languageapp.backend.observability.OperationsMetrics operations;

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(Exception ex,HttpServletRequest request) {
        if(operations!=null)operations.conflict(com.languageapp.backend.observability.OperationsMetrics.Conflict.CONSTRAINT);
        return ResponseEntity.status(409).body(buildErrorResponse(HttpStatus.CONFLICT,
                "Az adatok ütköznek egy meglévő vagy időközben megváltozott adattal. Frissíts, majd próbáld újra.",request.getRequestURI()));
    }
    @ExceptionHandler({org.springframework.dao.OptimisticLockingFailureException.class,jakarta.persistence.OptimisticLockException.class})
    public ResponseEntity<ErrorResponse> handleOptimistic(Exception ex,HttpServletRequest request) {
        if(operations!=null)operations.conflict(com.languageapp.backend.observability.OperationsMetrics.Conflict.OPTIMISTIC);
        return ResponseEntity.status(409).body(buildErrorResponse(HttpStatus.CONFLICT,
                "Az adatok időközben megváltoztak. Frissíts, majd próbáld újra.",request.getRequestURI()));
    }
    @ExceptionHandler({org.springframework.dao.PessimisticLockingFailureException.class,jakarta.persistence.LockTimeoutException.class,jakarta.persistence.PessimisticLockException.class})
    public ResponseEntity<ErrorResponse> handleLock(Exception ex,HttpServletRequest request) {
        if(operations!=null)operations.conflict(com.languageapp.backend.observability.OperationsMetrics.Conflict.LOCK);
        return ResponseEntity.status(409).header("Retry-After","1").body(buildErrorResponse(HttpStatus.CONFLICT,
                "Egy másik művelet még folyamatban van. Várj egy kicsit, majd próbáld újra.",request.getRequestURI()));
    }
    @ExceptionHandler(org.springframework.core.task.TaskRejectedException.class)
    public ResponseEntity<ErrorResponse> handleCapacity(Exception ex,HttpServletRequest request) {
        return ResponseEntity.status(503).header("Retry-After","1").body(buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE,
                "A kiszolgáló most foglalt. Próbáld újra később.",request.getRequestURI()));
    }
    @ExceptionHandler(org.springframework.dao.QueryTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleQueryTimeout(Exception ex,HttpServletRequest request) {
        return ResponseEntity.status(503).header("Retry-After","1").body(buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE,
                "A művelet most nem fejeződött be. Próbáld újra később.",request.getRequestURI()));
    }
    @ExceptionHandler({jakarta.validation.ConstraintViolationException.class,org.springframework.web.method.annotation.HandlerMethodValidationException.class})
    public ResponseEntity<ErrorResponse> handleMethodValidation(Exception ex,HttpServletRequest request) {
        return ResponseEntity.badRequest().body(buildErrorResponse(HttpStatus.BAD_REQUEST,"Hibás vagy hiányzó bemeneti adat.",request.getRequestURI()));
    }

    @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class,org.springframework.web.servlet.NoHandlerFoundException.class})
    public ResponseEntity<ErrorResponse> handleUnmappedRoute(Exception ex,HttpServletRequest request) {
        return ResponseEntity.status(404).body(buildErrorResponse(HttpStatus.NOT_FOUND,"A keresett végpont nem található.",request.getRequestURI()));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleThrottled(TooManyRequestsException ex,HttpServletRequest request) {
        return ResponseEntity.status(429).header("Retry-After",Long.toString(ex.retryAfterSeconds()))
                .header("Cache-Control","no-store").body(buildErrorResponse(HttpStatus.TOO_MANY_REQUESTS,ex.getMessage(),request.getRequestURI()));
    }

    @ExceptionHandler(TranslationUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleTranslationUnavailable(TranslationUnavailableException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", "30")
                .body(buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(buildErrorResponse(
                HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedContentType(org.springframework.web.HttpMediaTypeNotSupportedException ex,HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).headers(ex.getHeaders()).body(buildErrorResponse(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,"Nem támogatott tartalomtípus.",request.getRequestURI()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMethod(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).headers(ex.getHeaders())
                .body(buildErrorResponse(HttpStatus.METHOD_NOT_ALLOWED,
                        "Ez a HTTP-művelet itt nem használható.", request.getRequestURI()));
    }

    /** Missing, empty or invalid query parameters are client errors. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(buildErrorResponse(HttpStatus.BAD_REQUEST,
                "Missing or empty request parameter: " + ex.getParameterName(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleInvalidParameter(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(buildErrorResponse(HttpStatus.BAD_REQUEST,
                "Invalid request parameter: " + ex.getName(), request.getRequestURI()));
    }

    /** Invalid timestamp formats are client errors, including missing UTC offsets. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableRequest(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Invalid request body. Timestamps must use ISO 8601 with Z or an explicit offset.",
                request.getRequestURI()));
    }

    /**
     * Handles {@link ResourceNotFoundException}.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex,
            HttpServletRequest request) {

        log.warn("http.not_found");

        ErrorResponse errorResponse = buildErrorResponse(
                HttpStatus.NOT_FOUND,
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Handles {@link BadRequestException}.
     */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequestException(
            BadRequestException ex,
            HttpServletRequest request) {

        log.warn("http.bad_request");

        ErrorResponse errorResponse = buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles {@link ForbiddenException}.
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenException(ForbiddenException ex, HttpServletRequest request) {
        log.warn("http.forbidden");

        ErrorResponse errorResponse = buildErrorResponse(
                HttpStatus.FORBIDDEN,
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    /**
     * Handles Validation exceptions (e.g. @Valid on @RequestBody).
     * Extracts all field errors and joins them into a readable string.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("http.validation_failed");

        ErrorResponse errorResponse = buildErrorResponse(
                HttpStatus.BAD_REQUEST,
                errorMessage,
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handles {@link BadCredentialsException} thrown by Spring Security during login.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(
            BadCredentialsException ex,
            HttpServletRequest request) {

        log.warn("auth.credentials.rejected");

        ErrorResponse errorResponse = buildErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "Invalid email and/or password!",
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    /**
     * Handles all uncaught exceptions.
     * Prevents internal details from leaking to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception ex,
            HttpServletRequest request) {

        com.languageapp.backend.observability.SafeDiagnostics.failure(log,"http.unhandled",ex);

        ErrorResponse errorResponse = buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected internal server error occurred.",
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    @ExceptionHandler(org.springframework.security.authentication.DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabledException(
            org.springframework.security.authentication.DisabledException ex,
            HttpServletRequest request) {

        log.warn("auth.disabled");

        ErrorResponse errorResponse = buildErrorResponse(
                HttpStatus.FORBIDDEN,
                "Ez a felhasználói fiók fel lett függesztve vagy törölve lett.",
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    /**
     * Creates a standardized {@link ErrorResponse} object.
     */
    private ErrorResponse buildErrorResponse(
            HttpStatus status,
            String message,
            String path) {

        String safePath = path != null ? HtmlUtils.htmlEscape(path) : null;

        return new ErrorResponse(
                clock.instant(),
                status.value(),
                status.getReasonPhrase(),
                message,
                safePath
        );
    }
}
