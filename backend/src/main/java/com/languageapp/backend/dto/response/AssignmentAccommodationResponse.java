package com.languageapp.backend.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AssignmentAccommodationResponse(UUID userId, String studentName, String studentEmail,
        int extraMinutes, boolean unlimited, Instant availableUntilOverride,
        Integer effectiveTimeLimitMinutes, Instant effectiveAvailableUntil, boolean hasOngoingSession) {}
