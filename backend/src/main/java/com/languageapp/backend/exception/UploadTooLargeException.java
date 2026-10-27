package com.languageapp.backend.exception;

public class UploadTooLargeException extends RuntimeException {
    public UploadTooLargeException() {
        super("A profilkép legfeljebb 5 MB méretű lehet.");
    }
}
