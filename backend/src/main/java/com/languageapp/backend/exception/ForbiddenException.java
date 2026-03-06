package com.languageapp.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Custom exception to trigger a 403 Forbidden HTTP status code.
 * Used when an authenticated user attempts to access a resource they do not have permission for (e.g., IDOR attempts).
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}