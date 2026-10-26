package com.languageapp.backend.dto.request;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
public record InvitationRequest(
        @NotBlank @Pattern(regexp="friend|classroom", message="Érvénytelen meghívótípus.") String kind,
        @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}", message="Érvénytelen meghívóazonosító.") String token) { }
