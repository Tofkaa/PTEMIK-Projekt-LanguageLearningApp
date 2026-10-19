package com.languageapp.backend.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.Instant;

@Data
public class AssignmentAccommodationRequest {
    @NotNull @Min(0)
    private Integer extraMinutes = 0;
    private boolean unlimited;
    /** Null means the common assignment deadline. */
    private Instant availableUntilOverride;
}
