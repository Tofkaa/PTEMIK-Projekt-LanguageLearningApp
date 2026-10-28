package com.languageapp.backend.dto.request;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record DynamicPracticeCheckRequest(@NotNull UUID attemptId,@NotNull UUID exerciseId,@NotNull @Size(max=10000) String answer) { }
