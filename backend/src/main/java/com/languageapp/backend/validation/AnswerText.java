package com.languageapp.backend.validation;

import java.util.Locale;

/** The same comparison rules are used by evaluation and import consistency checks. */
public final class AnswerText {
    private AnswerText() { }
    public static String normalize(String value) {
        if (value == null || value.isBlank()) return "";
        return value.replaceAll("\\p{Punct}", "").toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
