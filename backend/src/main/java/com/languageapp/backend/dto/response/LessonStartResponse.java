package com.languageapp.backend.dto.response;

import java.util.List;
import java.util.UUID;

public record LessonStartResponse(UUID attemptId, String difficulty, boolean manualPractice,
                                  List<ExerciseResponse> exercises) {}
