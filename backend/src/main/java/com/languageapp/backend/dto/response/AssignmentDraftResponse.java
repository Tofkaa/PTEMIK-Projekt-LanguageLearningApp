package com.languageapp.backend.dto.response;

import java.time.Instant;
public record AssignmentDraftResponse(long revision, Instant savedAt, AssignmentTimingResponse timing) {}
