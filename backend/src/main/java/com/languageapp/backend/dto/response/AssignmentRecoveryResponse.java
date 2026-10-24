package com.languageapp.backend.dto.response;

import com.languageapp.backend.dto.request.AssignmentDraftRequest;
import java.time.Instant;

public record AssignmentRecoveryResponse(AssignmentStartResponse sessionData,
        AssignmentResponse assignmentDetails, AssignmentDraftRequest draft, long revision, Instant savedAt) {}
