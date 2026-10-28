package com.languageapp.backend.exception;

/** Temporary throttling only; rejected requests never extend an account lock. */
public class TooManyRequestsException extends BadRequestException {
    private final long retryAfterSeconds;
    public TooManyRequestsException(long seconds) {
        super("Túl sok kérés. Próbáld újra " + Math.max(1, seconds) + " másodperc múlva.");
        retryAfterSeconds = Math.max(1, seconds);
    }
    public long retryAfterSeconds() { return retryAfterSeconds; }
}
