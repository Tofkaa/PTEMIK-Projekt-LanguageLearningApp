package com.languageapp.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A versioned snapshot of student input; it never contains model answers. */
@Data
public class AssignmentDraftRequest {
    @Min(0) private long revision;
    @NotNull @Size(max = 2000) private List<@NotNull UUID> queue = new ArrayList<>();
    @NotNull @Size(max = 1000) @Valid private List<@NotNull ExerciseSubmission> answers = new ArrayList<>();
    @Min(0) private int currentIndex;
    @NotNull @Size(max = 10000) private String currentAnswer = "";
    private boolean reviewing;
}
