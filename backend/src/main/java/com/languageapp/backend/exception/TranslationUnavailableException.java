package com.languageapp.backend.exception;

/** Transient failure: never persist this message as a vocabulary translation. */
public class TranslationUnavailableException extends RuntimeException {
    public TranslationUnavailableException() {
        super("A fordítás most nem érhető el. A kért új szavakat nem mentettük; próbáld újra később.");
    }
}
