package com.languageapp.backend.exception;

/** A rejected code whose failed-attempt counter must commit with the HTTP 400 response. */
public class IncorrectOtpException extends BadRequestException {
    public IncorrectOtpException(String message) { super(message); }
}
