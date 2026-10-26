package com.languageapp.backend.dto.response;
import java.util.UUID;
public record InvitationPreview(String kind, String title, String description, String state, UUID classroomId) { }
