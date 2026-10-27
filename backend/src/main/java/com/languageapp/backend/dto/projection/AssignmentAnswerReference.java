package com.languageapp.backend.dto.projection;

import java.util.Map;
import java.util.UUID;

/** Teacher analytics fallback: only answers of exercises actually assigned in this classroom. */
public record AssignmentAnswerReference(UUID assignmentId, UUID exerciseId, Map<String, Object> correctAnswer) { }
