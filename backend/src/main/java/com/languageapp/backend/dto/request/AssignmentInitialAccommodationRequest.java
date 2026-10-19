package com.languageapp.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

/** Per-student timing submitted atomically with a new assignment. */
@Getter
@Setter
public class AssignmentInitialAccommodationRequest extends AssignmentAccommodationRequest {
    @NotNull
    private UUID userId;
}
