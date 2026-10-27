package com.languageapp.backend.validation;

import com.languageapp.backend.dto.request.ExerciseSubmission;
import com.languageapp.backend.exception.BadRequestException;
import java.util.List;

/** Shared transport/domain bounds; empty text remains a valid unanswered exercise. */
public final class AnswerLimits {
    public static final int MAX_CHARACTERS = 10_000;
    public static final int MAX_LESSON_SUBMISSIONS = 2_000;
    public static final int MAX_ASSIGNMENT_SUBMISSIONS = 1_000;
    public static final int MAX_CHECK_BYTES = 64 * 1024;
    public static final int MAX_BATCH_BYTES = 1024 * 1024;
    public static final int MAX_WORD_CHARACTERS = 100;
    public static final int MAX_WORDS = 1_000;
    private AnswerLimits() { }

    public static boolean isValid(Object answer) {
        return answer instanceof String text && text.length() <= MAX_CHARACTERS;
    }
    public static void requireText(Object answer) {
        if (!isValid(answer)) throw new BadRequestException("A válasz szöveg legyen, legfeljebb 10 000 karakterrel.");
    }
    public static void requireWord(String word) {
        if (word == null || word.isBlank() || word.length() > MAX_WORD_CHARACTERS)
            throw new BadRequestException("A szó nem lehet üres, és legfeljebb 100 karakter lehet.");
    }
    public static void requireWords(List<String> words) {
        if (words == null || words.size() > MAX_WORDS) throw new BadRequestException("Túl sok vagy hiányzó szó.");
        for (String word : words) {
            requireWord(word);
            requireWord(word.trim().toLowerCase(java.util.Locale.ROOT));
        }
    }
    public static void requireSubmissions(List<ExerciseSubmission> answers, int maximum) {
        if (answers == null || answers.size() > maximum)
            throw new BadRequestException("Túl sok vagy hiányzó feladatválasz.");
        for (ExerciseSubmission answer : answers) {
            if (answer == null || answer.getExerciseId() == null)
                throw new BadRequestException("Érvénytelen feladatválasz.");
            requireText(answer.getAnswer());
        }
    }
}
