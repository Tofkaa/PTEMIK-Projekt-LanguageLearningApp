package com.languageapp.backend.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Authoritative clock and session snapshot. A null deadline means no remaining time constraint. */
public record AssignmentTimingResponse(UUID sessionId, Instant startedAt, Integer timeLimitMinutes,
        Instant availableUntil, Instant deadline, boolean allowLateSubmission, boolean finished,
        Instant serverNow) {}
